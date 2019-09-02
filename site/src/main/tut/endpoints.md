---
layout: docs
title: Endpoint guide
position: 2
---

# Endpoint Guide
* [Overview](#overview)
* [Knowledge services](#knowledge-services)
  * [Age](#age-service)
  
## Overview
There are multiple endpoints, each has its own purpose. 

## Knowledge services
Knowlegde services can be queried or asked for information. They are a hotspot or interchange for information. 
A knowledge service can execute federated queries, this means that the query contains parts to be executed on other services. 
This can be the result of a query or some sort of assertion.

### Age service
This service can test if a persons satisfies certain age constraints.
It returns a boolean (```Content-Type: text/boolean```)
Request structure:
GET```/age?id={id}&minimumAge={age}&targetDate={targetDate}```  
or  
POST ```/age```  
body ```{"person":{"@id":"http://$host/person/$id1"},"minimumAge":18,"targetDate":"2019-09-09"}```
