module mapbox.offline.server {
    requires static java.annotation;

    requires jgridshift.core;
    requires jakarta.annotation;

    requires jai.core;

    requires jul.to.slf4j;

    requires thymeleaf;
    requires thymeleaf.spring6;
    requires org.yaml.snakeyaml;

    requires ejml.core;
    requires ejml.ddense;

    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires org.apache.tomcat.embed.core;
    requires org.apache.tomcat.embed.websocket;
    requires org.apache.commons.text;
    requires org.apache.logging.log4j;
    requires org.apache.tomcat.embed.el;

    requires systems.uom.common;
    requires systems.uom.quantity;
    requires si.uom.quantity;
    requires si.uom.units;
    requires tech.uom.lib.common;

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
    requires org.geotools.ogc.org.w3.xlink;
    requires org.geotools.http;

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

    requires org.hsqldb;

    requires org.apiguardian.api;

    requires commons.pool;

    requires com.zaxxer.hikari;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.module.paramnames;

    requires ch.qos.logback.core;
    requires ch.qos.logback.classic;

    requires io.swagger.v3.oas.annotations;

    requires javax.inject;

    requires re2j;
    requires unbescape;
    requires attoparser;
    requires GeographicLib.Java;

    opens io.github.qmjy.mapbox;
    opens io.github.qmjy.mapbox.controller;
    opens io.github.qmjy.mapbox.config;
    opens io.github.qmjy.mapbox.model;
    opens io.github.qmjy.mapbox.util;
    opens io.github.qmjy.mapbox.service;
}