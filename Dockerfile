FROM clojure
COPY . /usr/src/app
WORKDIR /usr/src/app
EXPOSE 5000
CMD ["lein", "run"]
