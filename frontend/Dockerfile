FROM nginx:1.30.6-alpine
ENV PORT=8080 API_ORIGEN=""
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY public/ /usr/share/nginx/html/
