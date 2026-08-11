# Two stages: a Node image that builds the static bundle, and an nginx image that ships only the
# result. Same shape as every backend service's JDK-build / JRE-run split -- the build tool never
# ships to production.

FROM node:22-alpine AS build
WORKDIR /build

# package*.json first, src last: editing a source file then re-runs only the build step, while the
# npm install layer stays cached.
#
# npm install, not npm ci: this lockfile is generated on Windows, and vite's rolldown dependency
# carries a separate optional native binding per OS/libc (win32, linux-gnu, linux-musl for this
# alpine image, ...). npm on Windows never populates the other platforms' lock entries, so `npm ci`
# here fails with "Missing: <pkg> from lock file" for the linux-musl variant no matter how often the
# lock is regenerated from a Windows host. npm install resolves for the platform it actually runs
# on instead of demanding the lockfile already match it.
COPY package.json package-lock.json ./
RUN npm install

COPY . .

# Vite inlines import.meta.env.VITE_* into the bundle at build time, not read at container startup
# -- there is no "runtime env" for a static SPA. The build arg lets docker-compose override it;
# the default matches careerbridge-frontend/.env, since it's the browser calling the gateway on
# localhost:8080, never this container calling it.
ARG VITE_API_BASE_URL=http://localhost:8080/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
