import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { buildSubgraphSchema } from '@apollo/subgraph';
import gql from 'graphql-tag';

import DataLoader from "dataloader";

function mapHotel(h) {
  return {
    id: h.id,
    name: h.name ?? null,
    city: h.city ?? null,
    stars: h.stars ?? null,
  };
}

function createHotelLoader(baseUrl) {
  return new DataLoader(async (ids) => {
    const results = await Promise.all(
      ids.map(async (id) => {
        try {
          const r = await fetch(`${baseUrl}/api/hotels/${id}`);
          if (!r.ok) return null;
          return mapHotel(await r.json());
        } catch {
          return null;
        }
      })
    );
    return results;
  });
}

const typeDefs = gql`
  type Hotel @key(fields: "id") {
    id: ID!
    name: String
    city: String
    stars: Int
  }

  type Query {
    hotelsByIds(ids: [ID!]!): [Hotel]
  }
`;

const resolvers = {
  Hotel: {
    __resolveReference: async ({ id }, { loaders }) => {
      return loaders.hotelById.load(id);
    },
  },
  Query: {
    hotelsByIds: async (_, { ids }, { loaders }) => {
      const res = await loaders.hotelById.loadMany(ids);
      return res.map(x => (x instanceof Error ? null : x));
    },
  },
};

const server = new ApolloServer({
  schema: buildSubgraphSchema([{ typeDefs, resolvers }]),
});

startStandaloneServer(server, {
  listen: { port: 4002 },
  context: async () => {
    const baseUrl = process.env.MONOLITH_BASE_URL || "http://monolith:8080";
    return { loaders: { hotelById: createHotelLoader(baseUrl) } };
  },
})
