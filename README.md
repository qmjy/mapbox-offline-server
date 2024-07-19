# map-offline-server

Map offline server with java(JDK 21).  
本项目旨在提供离线地图服务，为私有化地图部署提供解决方案。  
启动项目后可以通过 http://localhost:10101/ 查看开放接口，目前主要支持mbtiles、tpk两种地图格式数据。

![Mapbox Offline Demo](assets/chengdu.png)

## 特性列表

|           |        mbtiles         |        tpk         | shapefile |      osm.pbf       |   OSMB(geojson)    |
|:---------:|:----------------------:|:------------------:|:---------:|:------------------:|:------------------:|
|   底图切片    |   :white_check_mark:   | :white_check_mark: |           |                    |                    |
|   地理逆编码   |                        |                    |           |                    | :white_check_mark: |
|   地理编码    |                        |                    |           |                    |                    |
|   路径规划    |                        |                    |           | :white_check_mark: |                    |
|   POI搜索   | :white_check_mark:(矢量) |                    |           |                    |                    |
|   静态地图    |       :running:        |                    |           |                    |                    |
| 行政区划（含边界） |                        |                    |           |                    | :white_check_mark: |

1. 支持瓦片离线服务，主要用于Map服务器离线场景，也支持mvt（pbf）瓦片数据的元数据查看；
2. 支持全球行政区划级联数据查询，也支持对应行政区划边界数据查询，查询时支持国际化，支持地理逆编码；
3. 支持mbtiles格式的矢量数据POI搜索；
4. 支持静态资源HTTP服务器能力；
5. 支持工具能力：shapefile转geojson，支持mbtiles合并；

## 安装启动程序

假设您已经安装完成git、JDK21、Maven程序并设置好了环境变量。

```bash
git clone git@github.com:qmjy/mapbox-offline-server.git
```

进入到代码目录执行命令编码源码：

```bash
cd mapbox-offline-server
mvn clean package
```

进入编译结果目录并启动程序

```bash
cd target
java -jar mapbox-offline-server-xxx.jar --dataPath="your data path"
```

## 资源结构

> 本项目自带了WGS84(WKID=4326)、CGCS2000(WKID=4490)坐标系的Mapbox资源文件。

- mapbox离线服务至少需要包含四种资源文件：mapbox-gl、fonts、sprites、tilesets；
- 行政区划服务相关需需要包含一种资源文件：geojson；
- 在运行本服务之前需要按照如下结构配置数据目录，然后通过“--dataPath=xxx”的方式启动服务；
- mapbox最新版本不支持离线，可以使用[Maplibre](https://maplibre.org/)代替；

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
│      Chengdu.mbtiles
│      Chengdu.mbtiles.idx
└─OSMB
       China.geojson
```

## 资源

### 资源下载

数据获取可通过issue反馈需求并留下联系方式，已经支持的数据服务地址，请参考WIKI介绍。

### 字体下载

SimSun Regular（宋体）：https://pan.baidu.com/s/1lv69EP5QlaUnlKZlH4-qlA  
Microsoft YaHei Regular（雅黑）：https://pan.baidu.com/s/1-tJr-PpKSFRxlfhWwtc0Kw  
Microsoft YaHei Bold：https://pan.baidu.com/s/1Ls1hgLIbcu5impJ086x5DQ  
Arial Regular：https://pan.baidu.com/s/102-e8pYKB2CO9bvP3LvWug  
Other Fonts: https://github.com/developmentseed/osm-seed-visor/tree/master/fonts