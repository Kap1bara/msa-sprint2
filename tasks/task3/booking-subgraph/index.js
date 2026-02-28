import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import gql from 'graphql-tag';

import grpc from "@grpc/grpc-js";
import protoLoader from "@grpc/proto-loader";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const protoPath = path.join(__dirname, "booking.proto");
const pkgDef = protoLoader.loadSync(protoPath, { keepCase: true, defaults: true });
const proto = grpc.loadPackageDefinition(pkgDef);
const client = new proto.booking.BookingService(
  process.env.BOOKING_GRPC_ADDR || "booking-service:9090",
  grpc.credentials.createInsecure()
);

function listBookings(userId) {
  return new Promise((resolve, reject) => {
    client.ListBookings({ user_id: userId }, (err, resp) => {
      if (err) return reject(err);
      resolve(resp?.bookings || []);
    });
  });
}

const typeDefs = gql`
  type Booking @key(fields: "id") {
    id: ID!
    userId: String!
    hotelId: String!
    promoCode: String

    # базовое значение из booking-service (то, что сейчас приходит)
    discountPercent: Float

    # оригинал для promo-subgraph (чтобы было что показать в DiscountInfo.originalDiscount)
    discountPercentBase: Float

    hotel: Hotel
  }

  type Hotel @key(fields: "id") {
    id: ID!
  }

  type Query {
    userBookings(userId: ID!): [Booking]
    booking(id: ID!): Booking
  }
`;

const resolvers = {
  Query: {
    userBookings: async (_, { userId }, { req }) => {
      const authUserId = req.headers["userid"];
      if (!authUserId) return [];
      if (authUserId !== userId) return [];

      const bookings = await listBookings(userId);

      return bookings.map(b => ({
        id: b.id,
        userId: b.user_id,
        hotelId: b.hotel_id,
        promoCode: b.promo_code || null,
        discountPercent: b.discount_percent ?? 0,
        discountPercentBase: b.discount_percent ?? 0
      }));
    },
  },
  Booking: {
    hotel: (booking) => ({ __typename: "Hotel", id: booking.hotelId }),
  },
};

const server = new ApolloServer({
  schema: buildSubgraphSchema([{ typeDefs, resolvers }]),
});

startStandaloneServer(server, {
  listen: { port: 4001 },
  context: async ({ req }) => ({ req }),
}).then(() => {
  console.log('✅ Booking subgraph ready at http://localhost:4001/');
});
