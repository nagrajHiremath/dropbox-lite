# VITE_API_BASE_URL is baked into the static bundle at build time (Vite env
# vars are resolved at `vite build`, not read at container start) - pass the
# public Ingress URL the browser will actually call, not an in-cluster DNS
# name. See .github/workflows/deploy-dropbox-ui.yaml for the build-arg value.
FROM node:22-alpine AS build
WORKDIR /app

ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
