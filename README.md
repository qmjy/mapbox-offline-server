# map-offline-server

Map offline server with java(JDK 21).  
本项目旨在提供离线地图服务，为私有化地图部署提供解决方案。  
启动项目后可以通过 http://localhost:10101/ 查看开放接口，目前主要支持mbtiles、tpk两种地图格式数据。

![Mapbox Offline Demo](./assets/chengdu.png)

## 地图服务器数据能力矩阵

|           | mbtiles                | tpk                | geojson | shapefile | geopackage | osm.pbf            | OSMB(geojson)      |
|-----------|------------------------|--------------------|---------|-----------|------------|--------------------|--------------------|
| 底图切片      | :white_check_mark:     | :white_check_mark: |         |           |            |                    |                    |
| 地理逆编码     |                        |                    |         |           |            |                    | :white_check_mark: |
| 地理编码      |                        |                    |         |           |            |                    |                    |
| 路径规划      |                        |                    |         |           |            | :white_check_mark: |                    |
| POI搜索     | :white_check_mark:(矢量) |                    |         |           |            |                    |                    |
| 静态地图      | :running:              |                    |         |           |            |                    |                    |
| 行政区划（含边界） |                        |                    |         |           |            |                    | :white_check_mark: |


## 特性列表

1. 支持瓦片离线服务，主要用于Mapbox离线场景；

- 支持mbtiles瓦片数据发布；
- 支持mapbox离线静态资源发布；
- 支持底图数据的元数据查看；

2. 支持全球行政区划级联数据查询；

- 支持全球省市区县级联数据查询；
- 支持全球行政区划边界范围查询；
- 支持全球地理逆编码查询；
- 支持判断经纬度坐标是否在某一个行政区划范围内；
- 国际化支持；

3. 工具支持

- 支持mbtiles文件合并；
- 支持mvt(pbf)文件解析并以可读的形式展现；
- 支持POI离线服务器（自动从mbtiles数据解析）；

## 资源结构

> 本项目自带了WGS84(WKID=4326)、CGCS2000(WKID=4490)坐标系的Mapbox资源文件。

- mapbox离线服务至少需要包含四种资源文件：mapbox-gl、fonts、sprites、tilesets；
- 行政区划服务相关需需要包含一种资源文件：geojson；
- 在运行本服务之前需要按照如下结构配置数据目录，然后通过“--dataPath=xxx”的方式启动服务；
- mapbox最新版本不支持离线，可以使用Maplibre代替；

```bash
data
├─assets
│      mapbox-gl.css
│      mapbox-gl.js
├─fonts
│  └─Arial Regular
│         0-255.pbf
│         15616-15871.pbf
│         15872-16127.pbf
├─sprites
│  └─streets
│         sprite.json
│         sprite.png
│         sprite@2x.json
│         sprite@2x.png
├─styles
│      world.json
├─osm.pbf
│      Chengdu.osm.pbf
├─tilesets
│      Beijing.mbtiles
│      Beijing.mbtiles.idx
└─OSMB
       China.geojson
```

## 资源

### POI数据下载

POI数据可从[规划云](http://guihuayun.com/poi/)下载。

### 字体下载

SimSun Regular（宋体）：https://pan.baidu.com/s/1lv69EP5QlaUnlKZlH4-qlA  
Microsoft YaHei Regular（雅黑）：https://pan.baidu.com/s/1-tJr-PpKSFRxlfhWwtc0Kw  
Microsoft YaHei Bold：https://pan.baidu.com/s/1Ls1hgLIbcu5impJ086x5DQ  
Arial Regular：https://pan.baidu.com/s/102-e8pYKB2CO9bvP3LvWug
Other Fonts: https://github.com/developmentseed/osm-seed-visor/tree/master/fonts