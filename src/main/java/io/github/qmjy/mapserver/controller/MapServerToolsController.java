package io.github.qmjy.mapserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.io.IOUtils;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 提供地图工具类服务
 *
 * @author Shaofeng Liu
 */
@RestController
@RequestMapping("/api/tools")
@Tag(name = "地图工具服务", description = "地图服务工具接口能力")
public class MapServerToolsController {
    private static final Logger logger = LoggerFactory.getLogger(MapServerToolsController.class);

    @PostMapping(value = "/shp2geojson", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "shapefile转geojson", description = "将shapefile文件转换成geojson文件。")
    @ApiResponse(responseCode = "200", description = "成功响应", content = @Content(mediaType = "application/octet-stream", schema = @Schema(implementation = File.class)))
    public ResponseEntity<Resource> shp2geojson(@Parameter(description = "待转换的shapefile文件。要求上传的zip包中，shp所在文件夹下需要含有对应名称的shx文件，否则会返回400 Bad Request!")
                                                @RequestParam("shapeFileSetOfZip") MultipartFile shapeFileSetOfZip) {
        assert shapeFileSetOfZip.getOriginalFilename() != null;
        List<String> shapefiles = checkAndPrepare(shapeFileSetOfZip);
        if (shapefiles.isEmpty()) {
            return ResponseEntity.badRequest().build();
        } else {
            for (String shapefile : shapefiles) {
                if (!convertShapefileToGeoJson(shapefile, shapefile + ".geojson")) {
                    logger.error("转换GeoJson文件出错：{}", shapefile);
                    return ResponseEntity.internalServerError().build();
                }
            }

            File targetZipFile = null;
            try {
                targetZipFile = collectionGeoJsonFiles(shapefiles, shapeFileSetOfZip.getOriginalFilename());
                if (targetZipFile != null) {
                    return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + targetZipFile.getName() + "\"").body(new FileUrlResource(targetZipFile.toURI().toURL()));
                }
            } catch (IOException e) {
                logger.error("返回结果文件出错：{}", targetZipFile.getAbsolutePath());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    private File collectionGeoJsonFiles(List<String> shapefiles, String OriginalFilename) {
        String name = OriginalFilename.substring(0, OriginalFilename.lastIndexOf("."));
        File file = new File(System.getProperty("java.io.tmpdir") + name + ".geojson.zip");
        ZipOutputStream zipOutputStream = null;
        try {
            zipOutputStream = new ZipOutputStream(new FileOutputStream(file));
            for (String filePath : shapefiles) {
                addFolderToZip(filePath + ".geojson", zipOutputStream);
            }
            zipOutputStream.close();
            return file;
        } catch (IOException e) {
            logger.error("收集GeoJson文件出错!");
            return null;
        } finally {
            IOUtils.closeQuietly(zipOutputStream);
        }
    }


    private static void addFolderToZip(String filePath, ZipOutputStream zipOutputStream) throws IOException {
        File file = new File(filePath);
        zipOutputStream.putNextEntry(new ZipEntry(file.getName()));
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fileInputStream.read(bytes)) >= 0) {
                zipOutputStream.write(bytes, 0, length);
            }
        }
    }

    private List<String> checkAndPrepare(MultipartFile shapeFileSetOfZip) {
        if (shapeFileSetOfZip.getOriginalFilename() != null && shapeFileSetOfZip.getOriginalFilename().endsWith(".zip")) {
            List<String> shapefiles = new ArrayList<>();

            try (ZipInputStream zipInputStream = new ZipInputStream(shapeFileSetOfZip.getInputStream())) {
                byte[] buffer = new byte[1024];
                ZipEntry zipEntry = zipInputStream.getNextEntry();
                while (zipEntry != null) {
                    File newFile = new File(System.getProperty("java.io.tmpdir") + File.separator + zipEntry.getName());
                    if (zipEntry.isDirectory()) {
                        if (!newFile.mkdirs()) {
                            logger.info("解压缩的目标文件夹已经存在：{}", newFile.getAbsolutePath());
                        }
                    } else {
                        File parent = newFile.getParentFile();
                        if (!parent.exists()) {
                            if (!parent.mkdirs()) {
                                logger.error("解压缩创建文件夹失败!");
                                return new ArrayList<>();
                            }
                        }
                        try (FileOutputStream fos = new FileOutputStream(newFile)) {
                            int length;
                            while ((length = zipInputStream.read(buffer)) > 0) {
                                fos.write(buffer, 0, length);
                            }
                        }
                        if (newFile.getName().endsWith(".shp")) {
                            String shapefilePath = newFile.getAbsolutePath();
                            shapefiles.add(shapefilePath);

                            //判断即将生成的文件是否已经存在！
                            String geojsonFilePath = shapefilePath + ".geojson";
                            if (new File(geojsonFilePath).exists()) {
                                if (!new File(geojsonFilePath).delete()) {
                                    logger.error("目标geojson文件已存在且无法删除：{}", geojsonFilePath);
                                    return new ArrayList<>();
                                }
                            }
                        }
                    }
                    zipEntry = zipInputStream.getNextEntry();
                }
            } catch (IOException e) {
                logger.error("解压缩上传的文件出错！");
                return new ArrayList<>();
            }
            return shapefiles;
        } else {
            return new ArrayList<>();
        }
    }


    public boolean convertShapefileToGeoJson(String shapefilePath, String geoJsonFilePath) {
        ShapefileDataStore shapefileDataStore = null;
        OutputStream geoJsonOutputStream = null;
        try {
            File shapefile = new File(shapefilePath);
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
            shapefileDataStore = (ShapefileDataStore) dataStoreFactory.createDataStore(shapefile.toURI().toURL());

            geoJsonOutputStream = new FileOutputStream(geoJsonFilePath);
            FeatureJSON featureJSON = new FeatureJSON();

            String typeName = shapefileDataStore.getTypeNames()[0];
            FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = shapefileDataStore.getFeatureSource(typeName);
            FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection = featureSource.getFeatures();
            featureJSON.writeFeatureCollection(featureCollection, geoJsonOutputStream);

            shapefileDataStore.dispose();
        } catch (IOException e) {
            logger.error("Shape文件转换出错：{}", shapefilePath);
            return false;
        } finally {
            if (shapefileDataStore != null) {
                shapefileDataStore.dispose();
            }
            IOUtils.closeQuietly(geoJsonOutputStream);
        }
        logger.info("Shape文件转换完成：{}", shapefilePath);
        return true;
    }
}
