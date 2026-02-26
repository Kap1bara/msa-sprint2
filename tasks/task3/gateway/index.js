import { ApolloServer } from '@apollo/server';
import { startStandaloneServer } from '@apollo/server/standalone';
import { ApolloGateway, RemoteGraphQLDataSource, IntrospectAndCompose } from '@apollo/gateway';

const gateway = new ApolloGateway({
  supergraphSdl: new IntrospectAndCompose({
    subgraphs: [
      { name: 'booking-subgraph', url: 'http://booking-subgraph:4001/graphql' },
      { name: 'hotel-subgraph', url: 'http://hotel-subgraph:4002/graphql' },
      { name: 'promocode-subgraph', url: 'http://promocode-subgraph:4003/graphql' },
    ],
  }),
  buildService({ url }) {
    return new RemoteGraphQLDataSource({
      url,
      willSendRequest({ request, context }) {
        if (context.userid) request.http.headers.set('userid', context.userid);
      },
    });
  },
});

const server = new ApolloServer({ gateway });

startStandaloneServer(server, {
  listen: { port: 4000 },
  context: async ({ req }) => ({
    userid: req.headers['userid'] || null,
  }),
}).then(({ url }) => {
  console.log(`🚀 Gateway ready at ${url}`);
});
