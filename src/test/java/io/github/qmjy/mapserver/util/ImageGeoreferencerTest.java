package io.github.qmjy.mapserver.util;

import io.github.qmjy.mapserver.model.dto.GeometryPointDTO;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.Position2D;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImageGeoreferencerTest {
    private ImageGeoreferencer imageGeoreferencer = null;
    private static final int IMG_WIDTH = 1920;
    private static final int IMG_HEIGHT = 1080;


    @BeforeEach
    void setUp() {
        Position2D[] geoQuadrilateral = {new Position2D(103.89454044804097, 30.717587642808056),// 左上
                new Position2D(103.89535842095404, 30.71703562322753),// 右上
                new Position2D(103.89602506887906, 30.717345035866003),// 右下
                new Position2D(103.89590927609919, 30.718227505471475)// 左下
        };
        imageGeoreferencer = new ImageGeoreferencer(IMG_WIDTH, IMG_HEIGHT, geoQuadrilateral);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void transformImageToGeo() {
    }

    @Test
    void testTransformImageToGeo() {
        try {
            GeometryPointDTO geometryPointDTO = imageGeoreferencer.transformImageToGeo(new Position2D(0, 0));
            assert geometryPointDTO.getLng() == 103.89454044804097;
            assert geometryPointDTO.getLat() == 30.717587642808056;

            geometryPointDTO = imageGeoreferencer.transformImageToGeo(new Position2D(IMG_WIDTH, 0));
            assert String.valueOf(geometryPointDTO.getLng()).startsWith("103.8953584209540");
            assert String.valueOf(geometryPointDTO.getLat()).startsWith("30.7170356232275");
        } catch (TransformException e) {
            assert false;
        }
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