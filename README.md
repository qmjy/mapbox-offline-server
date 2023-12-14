# mapbox-offline-server

Mapbox GL offline server with java(JDK 21).

## 特性列表

1. 支持瓦片离线服务，主要用于Mapbox离线场景；

- 支持mbtiles瓦片数据发布；
- 支持mapbox离线静态资源发布；

2. 支持全球行政区划级联数据查询；

- 支持全球省市区县级联数据查询；
- 支持全球行政区划边界范围查询；
- 支持全球地理逆编码查询；
- 支持判断一个经纬度坐标是否在某一个行政区划范围内；
- 国际化支持；

## 资源结构

> 本项目自带了WGS84、CGCS2000坐标系的Mapbox资源文件。

mapbox离线服务至少需要包含四种资源文件：mapbox-gl、fonts、sprites、tilesets。
行政区划服务相关需需要包含一种资源文件：geojson。
在运行本服务之前需要按照如下结构配置数据目录，然后通过“--dataPath=xxx”的方式启动服务。

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
├─tilesets
│      Beijing.mbtiles
│      Hongkong.mbtiles
└─OSMB
       China.geojson
```

## 字体下载

SimSun Regular（宋体）：https://pan.baidu.com/s/1lv69EP5QlaUnlKZlH4-qlA  
Microsoft YaHei Regular（雅黑）：https://pan.baidu.com/s/1-tJr-PpKSFRxlfhWwtc0Kw  
Microsoft YaHei Bold：https://pan.baidu.com/s/1Ls1hgLIbcu5impJ086x5DQ  
Arial Regular：https://pan.baidu.com/s/102-e8pYKB2CO9bvP3LvWug