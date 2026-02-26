import { ApolloServer } from "@apollo/server";
import { startStandaloneServer } from "@apollo/server/standalone";
import { buildSubgraphSchema } from "@apollo/subgraph";
import gql from 'graphql-tag';

const typeDefs = gql`
  extend schema
    @link(
      url: "https://specs.apollo.dev/federation/v2.3",
      import: ["@key", "@external", "@override", "@requires"]
    )

  extend type Booking @key(fields: "id") {
    id: ID! @external
    promoCode: String @external

    discountPercentBase: Float @external
    # Переопределяем поле из booking-subgraph
    discountPercent: Float! @override(from: "booking-subgraph")

    # requires только на promoCode + discountPercentBase
    discountInfo: DiscountInfo @requires(fields: "promoCode discountPercentBase")
  }

  type DiscountInfo {
    isValid: Boolean!
    originalDiscount: Float!
    finalDiscount: Float!
    description: String
    expiresAt: String
    applicableHotels: [ID!]!
  }

  type Query {
    validatePromoCode(code: String!, hotelId: ID): DiscountInfo!
    activePromoCodes: [DiscountInfo!]!
  }
`;

async function validatePromoCodeForUser(monolithBaseUrl, code, userId) {
  const url = `${monolithBaseUrl}/api/promos/validate?code=${encodeURIComponent(code)}&userId=${encodeURIComponent(userId)}`;

  try {
    const r = await fetch(url, { method: "POST" });
    if (!r.ok) return null;

    // { code, discountPercent, active, vipOnly }
    return await r.json();
  } catch {
    return null;
  }
}

// Приводим ответ монолита к DiscountInfo
function toDiscountInfo({ isValid, originalDiscount, finalDiscount, promo }) {
  return {
    isValid,
    originalDiscount,
    finalDiscount,
    description: promo
      ? (promo.vipOnly ? "VIP only promo" : "Valid promo")
      : "Invalid promo",
    expiresAt: null,
    applicableHotels: [],
  };
}

const resolvers = {
  Booking: {
    async discountPercent(booking, _, ctx) {
      const original = typeof booking.discountPercentBase === "number"
        ? booking.discountPercentBase
        : 0;

      const code = booking.promoCode;

      if (!code || code.trim() === "") return original;

      const userId = ctx.userId;
      if (!userId) return original;

      const promo = await validatePromoCodeForUser(ctx.monolithBaseUrl, code, userId);
      if (!promo || promo.active === false) return 0;

      return Number(promo.discountPercent ?? 0);
    },

    async discountInfo(booking, _, ctx) {
      const original = typeof booking.discountPercentBase === "number"
        ? booking.discountPercentBase
        : 0;

      const code = booking.promoCode;

      if (!code || code.trim() === "") {
        return toDiscountInfo({ isValid: false, originalDiscount: original, finalDiscount: 0, promo: null });
      }

      const userId = ctx.userId;
      if (!userId) {
        return toDiscountInfo({ isValid: false, originalDiscount: original, finalDiscount: 0, promo: null });
      }

      const promo = await validatePromoCodeForUser(ctx.monolithBaseUrl, code, userId);
      const isValid = Boolean(promo && promo.active !== false);
      const finalDiscount = isValid ? Number(promo.discountPercent ?? 0) : 0;

      return toDiscountInfo({
        isValid,
        originalDiscount: original,
        finalDiscount,
        promo: isValid ? promo : null,
      });
    },
  },

  Query: {
    async validatePromoCode(_, { code }, ctx) {
      const userId = ctx.userId;
      if (!userId) {
        return toDiscountInfo({ isValid: false, originalDiscount: 0, finalDiscount: 0, promo: null });
      }

      const promo = await validatePromoCodeForUser(ctx.monolithBaseUrl, code, userId);
      const isValid = Boolean(promo && promo.active !== false);
      const disc = isValid ? Number(promo.discountPercent ?? 0) : 0;

      return toDiscountInfo({ isValid, originalDiscount: disc, finalDiscount: disc, promo: isValid ? promo : null });
    },

    async activePromoCodes() {
      // В монолите нет публичного списка активных промокодов.
      return [];
    },
  },
};

const server = new ApolloServer({
  schema: buildSubgraphSchema([{ typeDefs, resolvers }]),
});

const port = Number(process.env.PORT || 4003);

const { url } = await startStandaloneServer(server, {
  listen: { port },
  context: async ({ req }) => ({
    monolithBaseUrl: process.env.MONOLITH_BASE_URL || "http://monolith:8080",
    userId: req.headers["userid"] || null,
  }),
});

console.log(`promocode-subgraph ready at ${url}`);
