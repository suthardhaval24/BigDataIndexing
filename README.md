# Big Data Indexing : Redis/ ElasticSearch/ Kibana

## Application Technology: Spring Boot

## Description:
The application helps in selecting HealthPlan and utilizes the JSON schema to store the documents into Redis.
Redis(Jedis) internal queueing mechanism is used to push the data into elastic search server for indexing.


Different elastic queries are used in Kibana to search for particular health plan document from the store.

* Etag generation for all types of requests
* OAuth implementation for token validation
* Redis DB is used for JSON document storage
* Elastic Search document indexing
* Kibana dashboard is used for querying the document data