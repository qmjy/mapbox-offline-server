module mapbox.offline.server {
    requires spring.web;
    requires spring.boot;
    requires spring.beans;
    requires spring.core;
    requires spring.context;
    requires spring.tx;
    requires spring.webmvc;
    requires spring.jdbc;
    requires spring.boot.starter.jdbc;
    requires spring.boot.autoconfigure;
    requires spring.boot.starter.thymeleaf;
    requires spring.boot.starter.web;
    requires spring.boot.starter.tomcat;
    requires spring.boot.starter;
    requires org.slf4j;
    requires org.xerial.sqlitejdbc;
    requires org.apache.tomcat.embed.core;
    requires org.geotools.api;
    requires org.geotools.metadata;
    requires org.geotools.main;
    requires org.geotools.geojson_core;
    requires static java.annotation;
    requires static lombok;
}