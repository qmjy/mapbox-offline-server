package io.github.qmjy.mapserver.util;

import org.geotools.geometry.Position2D;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImageGeoreferencerTest {
    ImageGeoreferencer imageGeoreferencer = null;

    @BeforeEach
    void setUp() {
        Position2D[] geoQuadrilateral = {
                new Position2D(103.89454044804097, 30.717587642808056),// 左上
                new Position2D(103.89535842095404, 30.71703562322753),// 右上
                new Position2D(103.89602506887906, 30.717345035866003),// 右下
                new Position2D(103.89590927609919, 30.718227505471475)// 左下
        };
        imageGeoreferencer = new ImageGeoreferencer(1920, 1080, geoQuadrilateral);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void transformImageToGeo() {
    }

    @Test
    void testTransformImageToGeo() {
    }

    @Test
    void transformGeoToImage() {
    }

    @Test
    void testTransformGeoToImage() {
    }

    @Test
    void getGeoQuadrilateral() {
    }
}