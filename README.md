# mapbox-offline-server
Mapbox GL offline server with java

## 资源结构
mapbox离线服务至少需要包含四种资源文件，mapbox-gl、fonts、sprites、map。
在运行本服务之前需要按照如下结构配置数据目录，然后通过“--dataPath=xxx”的方式启动服务。

```bash
data
│  
├─assets
│      mapbox-gl.css
│      mapbox-gl.js
│      
├─fonts
│  └─Arial Regular
│         0-255.pbf
│         15616-15871.pbf
│         15872-16127.pbf
│          
├─sprites
│  └─streets
│          sprite.json
│          sprite.png
│          sprite@2x.json
│          sprite@2x.png
│          
├─styles
│      world.json
│      
└─tilesets
        Beijing.mbtiles
        Hongkong.mbtiles
```

## 字体下载
SimSun Regular（宋体）：https://pan.baidu.com/s/1lv69EP5QlaUnlKZlH4-qlA  
Microsoft YaHei Regular（雅黑）：https://pan.baidu.com/s/1-tJr-PpKSFRxlfhWwtc0Kw  
Microsoft YaHei Bold：https://pan.baidu.com/s/1Ls1hgLIbcu5impJ086x5DQ  
Arial Regular：https://pan.baidu.com/s/102-e8pYKB2CO9bvP3LvWug