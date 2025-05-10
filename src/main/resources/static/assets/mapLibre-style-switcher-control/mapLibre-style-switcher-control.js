/* https://github.com/astridx/maplibreexamples/blob/main/plugins/maplibre-style-switcher.html */
class MapLibreStyleSwitcherControl {
        constructor(styles, defaultStyle) {
          this.styles = styles || MapLibreStyleSwitcherControl.DEFAULT_STYLES;
          this.defaultStyle =
            defaultStyle || MapLibreStyleSwitcherControl.DEFAULT_STYLE;
          this.onDocumentClick = this.onDocumentClick.bind(this);
        }
        getDefaultPosition() {
          const defaultPosition = "top-right";
          return defaultPosition;
        }
        onAdd(map) {
          this.map = map;
          this.controlContainer = document.createElement("div");
          this.controlContainer.classList.add("maplibregl-ctrl");
          this.controlContainer.classList.add("maplibregl-ctrl-group");
          this.mapStyleContainer = document.createElement("div");
          this.styleButton = document.createElement("button");
          this.styleButton.type = "button";
          this.mapStyleContainer.classList.add("maplibregl-style-list");
          for (const style of this.styles) {
            const styleElement = document.createElement("button");
            styleElement.type = "button";
            styleElement.innerText = style.title;
            styleElement.classList.add(
              style.title.replace(/[^a-z0-9-]/gi, "_")
            );
            styleElement.dataset.uri = JSON.stringify(style.uri);
            styleElement.addEventListener("click", (event) => {
              const srcElement = event.srcElement;
              if (srcElement.classList.contains("active")) {
                return;
              }
              this.map.setStyle(JSON.parse(srcElement.dataset.uri));
              this.mapStyleContainer.style.display = "none";
              this.styleButton.style.display = "block";
              const elms =
                this.mapStyleContainer.getElementsByClassName("active");
              while (elms[0]) {
                elms[0].classList.remove("active");
              }
              srcElement.classList.add("active");
            });
            if (style.title === this.defaultStyle) {
              styleElement.classList.add("active");
            }
            this.mapStyleContainer.appendChild(styleElement);
          }
          this.styleButton.classList.add("maplibregl-ctrl-icon");
          this.styleButton.classList.add("maplibregl-style-switcher");
          this.styleButton.addEventListener("click", () => {
            this.styleButton.style.display = "none";
            this.mapStyleContainer.style.display = "block";
          });
          document.addEventListener("click", this.onDocumentClick);
          this.controlContainer.appendChild(this.styleButton);
          this.controlContainer.appendChild(this.mapStyleContainer);
          return this.controlContainer;
        }
        onRemove() {
          if (
            !this.controlContainer ||
            !this.controlContainer.parentNode ||
            !this.map ||
            !this.styleButton
          ) {
            return;
          }
          this.styleButton.removeEventListener("click", this.onDocumentClick);
          this.controlContainer.parentNode.removeChild(this.controlContainer);
          document.removeEventListener("click", this.onDocumentClick);
          this.map = undefined;
        }
        onDocumentClick(event) {
          if (
            this.controlContainer &&
            !this.controlContainer.contains(event.target) &&
            this.mapStyleContainer &&
            this.styleButton
          ) {
            this.mapStyleContainer.style.display = "none";
            this.styleButton.style.display = "block";
          }
        }
      }
      // https://cloud.maptiler.com/maps/
      // https://github.com/maplibre/demotiles
      MapLibreStyleSwitcherControl.DEFAULT_STYLE = "Demotiles";
      MapLibreStyleSwitcherControl.DEFAULT_STYLES = [
        {
          title: "Demotiles",
          uri: "https://demotiles.maplibre.org/style.json",
        },
        {
          title: "Baisc",
          uri:
            "https://api.maptiler.com/maps/basic/style.json?key=" + config.MAPTILER_TOKEN,
        },
        {
          title: "Light",
          uri:
            "https://api.maptiler.com/maps/bright/style.json?key=" + config.MAPTILER_TOKEN,
        },
        {
          title: "Outdoors",
          uri:
            "https://api.maptiler.com/maps/outdoor/style.json?key=" + config.MAPTILER_TOKEN,
        },
        {
          title: "Satellite Hybrid",
          uri:
            "https://api.maptiler.com/maps/hybrid/style.json?key=" + config.MAPTILER_TOKEN,
        },
        {
          title: "Streets",
          uri:
            "https://api.maptiler.com/maps/streets/style.json?key=" + config.MAPTILER_TOKEN,
        },
      ];