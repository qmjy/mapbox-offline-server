module mapbox.offline.server {
    requires static java.annotation;

    requires jgridshift.core;

    requires jai.core;

    requires thymeleaf;
    requires thymeleaf.spring6;
    requires org.yaml.snakeyaml;

    requires unbescape;
    requires attoparser;

    requires ejml.core;
    requires ejml.ddense;

    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires org.apache.tomcat.embed.core;
    requires org.apache.commons.text;
    requires org.apache.logging.slf4j;

    requires systems.uom.common;

    requires micrometer.observation;
    requires micrometer.commons;

    requires org.locationtech.jts;

    requires org.geotools.main;
    requires org.geotools.api;
    requires org.geotools.metadata;
    requires org.geotools.geojson_core;
    requires org.geotools.epsg_hsql;
    requires org.geotools.referencing;
    requires org.geotools.ogc.net.opengis.ows;

    requires spring.jdbc;
    requires spring.web;
    requires spring.boot;
    requires spring.beans;
    requires spring.core;
    requires spring.context;
    requires spring.expression;
    requires spring.tx;
    requires spring.jcl;
    requires spring.aop;
    requires spring.webmvc;

    requires spring.boot.starter.thymeleaf;
    requires spring.boot.starter.json;
    requires spring.boot.starter.jdbc;
    requires spring.boot.autoconfigure;
    requires spring.boot.starter.web;
    requires spring.boot.starter.tomcat;
    requires spring.boot.starter;
    requires spring.boot.starter.logging;

    requires org.slf4j;
    requires org.xerial.sqlitejdbc;

    requires org.eclipse.emf.ecore;
    requires org.eclipse.emf.ecore.xmi;
    requires org.eclipse.emf.common;

    requires commons.pool;
    requires javax.inject;

    requires GeographicLib.Java;
}