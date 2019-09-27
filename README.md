# The Age Test API
This project ...

## Getting started

### Modules

- `ns`: namespace definitions (ontologies and properties), Semantic schema repository
- `api`: rest-api's for agetesting
- `service`: api implementation published to docker-hub

AgeTest modules are available for Scala 2.12.x. 
To include `xx` add the following to your `build.sbt`:
```
libraryDependencies += "nl.convenantgemeenten.agetest" %% "{xx}" % "{version}"
```

### Running the service
The demo-service default setup is linked to [Haal Centraal](https://github.com/VNG-Realisatie/Haal-Centraal-BRP-bevragen) for reading birthDate data. 
This remote service needs and api-key which must be provided in a ```secrets.conf``` file. 

Run local:
```
sbt service/run
```
or with docker:
```
cd examples
docker-compose up
```
