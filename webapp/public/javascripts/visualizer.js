/* This program is free software: you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public License
   as published by the Free Software Foundation, either version 3 of
   the License, or (at your option) any later version.
   
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   
   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

var dataUrl = "/api/traces?vehicleId=";
var coordUrl = "/api/convertToLatLon?";
var startLatLng = new L.LatLng(10.3181373, 123.8956844); // Portland OR

var map;

var vertexLayer = null, edgeLayer = null;

var cloudmadeUrl = 'http://{s}.tiles.mapbox.com/v3/mapbox.mapbox-streets/{z}/{x}/{y}.png';
var cloudmadeAttrib = 'Map data &copy; 2011 OpenStreetMap contributors, Imagery &copy; 2011 CloudMade';
var cloudmadeOptions = {
  maxZoom : 17,
  attribution : cloudmadeAttrib
};

var pointsGroup = new L.LayerGroup();
var edgeGroup = new L.LayerGroup();
var inferredGroup = new L.LayerGroup();
var actualGroup = new L.LayerGroup();
var evaluatedGroup = new L.LayerGroup();
var addedGroup = new L.LayerGroup();

var lines = null;

var i = 0;

var marker1 = null;
var marker2 = null;

var interval = null;
var paths = null;

var MAX_SPEED = 10; // in m/s

$(document).ready(function() {

  map = new L.Map('map');

  var cloudmade = new L.TileLayer(cloudmadeUrl, cloudmadeOptions);
  map.addLayer(cloudmade);
  map.addLayer(pointsGroup);
  map.addLayer(edgeGroup);
  map.addLayer(inferredGroup);
  map.addLayer(actualGroup);
  map.addLayer(evaluatedGroup);
  map.addLayer(addedGroup);

  map.setView(startLatLng, 17, true);

  var layersControl = new L.Control.Layers();
  layersControl.addBaseLayer(cloudmade, "base");
  layersControl.addOverlay(pointsGroup, "points");
  layersControl.addOverlay(edgeGroup, "edges");
  layersControl.addOverlay(inferredGroup, "inferred");
  layersControl.addOverlay(actualGroup, "actual");
  layersControl.addOverlay(evaluatedGroup, "evaluated");
  layersControl.addOverlay(addedGroup, "user added");

  map.addControl(layersControl);

  $("#controls").hide();
  $("#pause").hide();

  $("#loadDataLink").click(loadData);
  $("#next").click(nextPoint);
  $("#prev").click(prevPoint);
  $("#play").click(playData);
  $("#pause").click(pauseData);
  $("#playData").click(playData);
  $(".ui-accordion-container").accordion({
    active : "a.default",
    header : "a.accordion-label",
    fillSpace : true,
    alwaysOpen : false
  });
  $("#filterControls").tabs();

  $("#addCoordinates").click(addCoordinates);
  $("#addEdge").click(addEdge);

});

function addEdge() {

  var id = jQuery('#edge_id').val();
  drawEdge(id, null, EdgeType.ADDED);
  map.invalidateSize();
}

function drawCoords(lat, lon, popupMessage, pan) {
  var latlng = new L.LatLng(parseFloat(lat), parseFloat(lon));
  marker = new L.Circle(latlng, 10, {
    color : '#0c0',
    lat : parseFloat(lat),
    lon : parseFloat(lon),
    weight : 1
  });
  $(this).data('marker', marker);
  pointsGroup.addLayer(marker);
  addedGroup.addLayer(marker);
  if (popupMessage != null)
    marker.bindPopup(popupMessage);
  if (pan)
    map.panTo(latlng);

  return marker;
}

function drawProjectedCoords(x, y, popupMessage, pan) {
  var coordGetString = "x=" + x + "&y=" + y;
  var marker = null;
  $.ajax({
    url : coordUrl + coordGetString,
    dataType : 'json',
    async : false,
    success : function(data) {

      lat = data['x'];
      lon = data['y'];

      marker = drawCoords(lat, lon, popupMessage, pan);

    }
  });

  map.invalidateSize();

  return marker;
}

function addCoordinates() {

  var coordString = jQuery('#coordinate_data').val();
  var coordSplit = coordString.split(",", 2);

  if (coordSplit.length == 2) {
    var coordGetString = "x=" + coordSplit[0] + "&y=" + coordSplit[1];

    $.get(coordUrl + coordGetString, function(data) {

      lat = data['x'];
      lon = data['y'];

      var latlng = new L.LatLng(parseFloat(lat), parseFloat(lon));
      var new_marker = new L.Circle(latlng, 10, {
        color : '#0c0',
        lat : parseFloat(lat),
        lon : parseFloat(lon)
      });
      pointsGroup.addLayer(new_marker);
      addedGroup.addLayer(new_marker);
      map.panTo(latlng);
    });

    map.invalidateSize();
  }

}

function loadData() {

  var vehicleId = jQuery('#vehicle_id').val();
  var thisDataUrl = dataUrl + vehicleId;
  $.get(thisDataUrl, function(data) {

    $("#loadData").hide();

    lines = data;
    $("#controls").show();

    initSlider();

    map.invalidateSize();
  });

}

function initSlider() {
  $("#slider").slider({
    min : 0,
    max : lines.length
  });

  $("#slider").bind("slidechange", function() {
    i = $("#slider").slider("option", "value");
    moveMarker();
  });

  $("#slider").bind("slide", function(event, ui) {
    pauseData();
  });

}

function playData() {
  $("#play").hide();
  $("#pause").show();

  interval = setInterval(moveMarker, 1.5 * 1000);
}

function pauseData() {
  $("#play").show();
  $("#pause").hide();

  clearInterval(interval);
}

function nextPoint() {
  pauseData();

  $("#slider").slider("option", "value", i);
}

function prevPoint() {
  pauseData();

  i = i - 2;

  $("#slider").slider("option", "value", i);
}

function moveMarker() {
  if (i != $("#slider").slider("option", "value"))
    $("#slider").slider("option", "value", i);

  var done = renderMarker();

  if (!done)
    i++;
}

function drawResults(mean, major, minor, pointType) {

  var color;
  var fill;
  var groupType;
  if (pointType == PointType.INFERRED_FREE) {
    color = 'red';
    fill = false;
    groupType = inferredGroup;
  } else if (pointType == PointType.INFERRED_EDGE) {
    color = 'red';
    fill = true;
    groupType = inferredGroup;
  } else if (pointType == PointType.ACTUAL_FREE) {
    color = 'black';
    fill = false;
    groupType = actualGroup;
  } else if (pointType == PointType.ACTUAL_EDGE) {
    color = 'black';
    fill = true;
    groupType = actualGroup;
  }

  var meanCoords = new L.LatLng(parseFloat(mean.x), parseFloat(mean.y));

  if (major && minor) {
    var majorAxis = new L.Polyline([ meanCoords,
        new L.LatLng(parseFloat(major.x), parseFloat(major.y)) ], {
      fill : true,
      color : '#c00'
    });

    pointsGroup.addLayer(majorAxis);
    groupType.addLayer(majorAxis);

    var minorAxis = new L.Polyline([ meanCoords,
        new L.LatLng(parseFloat(minor.x), parseFloat(minor.y)) ], {
      fill : true,
      color : '#c0c'
    });

    pointsGroup.addLayer(minorAxis);
    groupType.addLayer(minorAxis);
  }

  var mean = new L.Circle(meanCoords, 5, {
    fill : fill,
    color : color,
    opacity : 1.0
  });

  pointsGroup.addLayer(mean);
  groupType.addLayer(mean);

}

PointType = {
  INFERRED_FREE : 0,
  INFERRED_EDGE : 1,
  ACTUAL_FREE : 2,
  ACTUAL_EDGE : 3
}

function renderMarker() {
  if (i >= 0 && i < lines.length) {
    pointsGroup.clearLayers();
    edgeGroup.clearLayers();
    actualGroup.clearLayers();
    inferredGroup.clearLayers();
    addedGroup.clearLayers();
    evaluatedGroup.clearLayers();

    /*
     * Draw lines first
     */
    renderGraph();

    if (lines[i].infResults) {
      var pointType = PointType.INFERRED_FREE;

      if (lines[i].infResults.pathSegmentIds.length > 0) {
        pointType = PointType.INFERRED_EDGE;
      }

      var results = lines[i].infResults;
      drawResults(results.meanCoords, results.majorAxisCoords,
          results.minorAxisCoords, pointType);
    }

    if (lines[i].actualResults) {
      var pointType = PointType.ACTUAL_FREE;

      if (lines[i].actualResults.pathSegmentIds.length > 0) {
        pointType = PointType.ACTUAL_EDGE;
      }

      var results = lines[i].actualResults;
      drawResults(results.meanCoords, results.majorAxisCoords,
          results.minorAxisCoords, pointType);
    }

    var obsCoords = new L.LatLng(parseFloat(lines[i].observedCoords.x),
        parseFloat(lines[i].observedCoords.y));
    var obs = new L.Circle(obsCoords, 10, {
      fill : true,
      color : 'grey',
      opacity : 1.0
    });
    pointsGroup.addLayer(obs);

    map.panTo(obsCoords);

    $("#count_display").html(lines[i].time + ' (' + i + ')');
    return false;
  } else {
    clearInterval(interval);
    return true;
  }
}

EdgeType = {
  ACTUAL : 0,
  INFERRED : 1,
  EVALUATED : 2,
  ADDED : 3
}

function drawEdge(id, velocity, edgeType) {

  var result = null;
  $.ajax({
    url : '/api/segment?segmentId=' + id,
    dataType : 'json',
    async : false,
    success : function(data) {

      var avg_velocity = Math.abs(velocity);

      var color;
      var weight = 5;
      var opacity = 0.7;

      var groupType;
      if (edgeType == EdgeType.INFERRED) {
        if (avg_velocity != null && avg_velocity < MAX_SPEED)
          color = '#' + getColor(avg_velocity / MAX_SPEED);
        else
          color = "red";

        weight = 10;
        opacity = 0.3;
        groupType = inferredGroup;
      } else if (edgeType == EdgeType.ACTUAL) {
        color = "black";
        weight = 2;
        opacity = 1.0;
        groupType = actualGroup;
      } else if (edgeType == EdgeType.EVALUATED) {
        color = "blue";
        weight = 20;
        opacity = 0.2;
        groupType = evaluatedGroup;
      } else if (edgeType == EdgeType.ADDED) {
        color = "green";
        weight = 20;
        opacity = 0.2;
        groupType = addedGroup;
      }

      var geojson = new L.GeoJSON();
      geojson.on('featureparse', function(e) {
        e.layer.setStyle({
          color : e.properties.color,
          weight : weight,
          opacity : opacity
        });
        if (e.properties && e.properties.popupContent) {
          e.layer.bindPopup(e.properties.popupContent);
        }
      });

      var escName = data.name.replace(/([\\<\\>'])/g, "");

      data.geom.properties = {
        popupContent : escName,
        color : color
      };

      var layers = new Array(geojson);
      geojson.addGeoJSON(data.geom);

      var angle = data.angle;
      if (angle != null) {
        var lonlat = data.geom.coordinates[data.geom.coordinates.length - 1];
        var myicon = new MyIcon();
        var arrowhead = new L.Marker.Compass(
            new L.LatLng(lonlat[1], lonlat[0]), {
              icon : myicon,
              clickable : false
            });
        arrowhead.setIconAngle(angle);
        layers.push(arrowhead);
      }

      result = new L.LayerGroup(layers);
      edgeGroup.addLayer(result);
      groupType.addLayer(result);
      map.invalidateSize();

    }
  });

  return result;
}

MyIcon = L.Icon.extend({
  iconUrl : '/public/images/tab_right.png',
  shadowUrl : null,
  shadowSize : null,
  iconAnchor : new L.Point(10, 22)
});

function createMatrixString(matrix) {
  var resStr = new Array();
  // var covLen = matrix.length;
  // var cols = Math.sqrt(covLen);
  $.each(matrix, function(index, data) {
    var tmpStr = parseFloat(data).toFixed(2);
    // if (index == 0)
    // resStr = "[[" + resStr;
    // if (index % cols == 0)
    // resStr = resStr + "],[";
    resStr.push(tmpStr);
  });

  return resStr;
}

function getPathName(pathSegmentIds) {
  var resStr = new Array();
  $.each(pathSegmentIds, function(index, data) {
    resStr.push(data[0]);
  });

  return paths[resStr];
}

function renderParticles() {
  var vehicleId = jQuery('#vehicle_id').val();
  $
      .get(
          '/api/traceParticleRecord',
          {
            vehicleId : vehicleId,
            recordNumber : i,
            particleNumber : -1,
            withParent : true
          },
          function(data) {

            var particleList = jQuery("#posteriorParticles");
            particleList.empty();

            var particleNumber = 0;
            jQuery
                .each(
                    data,
                    function(_, particleData) {

                      var particleMeanLoc = particleData.particle.infResults.meanCoords;
                      var locLinkName = 'particle' + particleNumber + '_mean';
                      var coordPair = particleMeanLoc.x + ','
                          + particleMeanLoc.y;
                      var locLink = '<a name="'
                          + locLinkName
                          + '" title="'
                          + coordPair
                          + '" style="color : black" href="javascript:void(0)">mean</a>';

                      var edgeDesc = "free";
                      var edgeId = particleData.particle.infResults.inferredEdge.id;
                      if (edgeId != null) {
                        edgeDesc = edgeId;
                      }
                      var edgeLinkName = 'particle' + particleNumber + '_edge';
                      var edgeLink = '<a name="'
                          + edgeLinkName
                          + '" title="'
                          + edgeDesc
                          + '" style="color : black" href="javascript:void(0)">'
                          + edgeDesc + '</a>';

                      var particleDivId = 'particle' + particleNumber;

                      var optionDiv = jQuery('<div>' + ' ('
                          + parseFloat(particleData.weight).toFixed(2) + '), '
                          + locLink + ', ' + edgeLink + '</div>');
                      // optionDiv.attr("value", particleNumber);

                      if (particleData.isBest)
                        optionDiv.css('background', 'yellow');

                      particleList
                          .append('<a class="accordion-label" href="#">'
                              + particleNumber + '</a>');
                      particleList.append(optionDiv);

                      $('#' + particleDivId).click(function() {
                        if (this.className == 'collapser') {
                          if (this.parentNode.classList.contains("collapsed"))
                            this.parentNode.classList.remove("collapsed");
                          else
                            this.parentNode.classList.add("collapsed");
                        }
                      });

                      createHoverPointLink(locLinkName, particleMeanLoc);

                      var subList = jQuery('<ul><li><div class="subinfo"></div></li></ul>');
                      var collapsedDiv = subList.find(".subinfo");
                      optionDiv.append(subList);

                      var stateMean = createMatrixString(particleData.particle.infResults.stateMean);
                      collapsedDiv.append('<li>state=' + stateMean + '</li>');

                      var stateCov = createMatrixString(particleData.particle.infResults.stateCovariance);
                      collapsedDiv.append('<li>stateCov=' + stateCov + '</li>');

                      var pathName = getPathName(particleData.particle.infResults.pathSegmentIds);
                      var pathData = $('#' + pathName).data('path');
                      var pathLikelihood = parseFloat(pathData.totalLogLikelihood).toFixed(2); 
                      collapsedDiv.append('<li>' + pathName + ', ' + pathLikelihood + '</li>');
                      
                      if (edgeId != null) {
                        createHoverLineLink(edgeLinkName, edgeId);
                      }

                      if (particleData.parent) {
                        var parentList = jQuery("<ul></ul>");
                        collapsedDiv.append(parentList);

                        var parentEdgeDesc = "free";
                        var parentEdgeId = particleData.parent.infResults.inferredEdge.id;
                        if (parentEdgeId != null) {
                          parentEdgeDesc = parentEdgeId;
                        }
                        var parentEdgeLinkName = 'parent_particle'
                            + particleNumber + '_edge';
                        var parentEdgeLink = '<a name="'
                            + parentEdgeLinkName
                            + '" title="'
                            + parentEdgeDesc
                            + '" style="color : black" href="javascript:void(0)">'
                            + parentEdgeDesc + '</a>';

                        var parentParticleMeanLoc = particleData.parent.infResults.meanCoords;
                        var parentLocLinkName = 'parent_particle'
                            + particleNumber + '_mean';
                        var parentCoordPair = parentParticleMeanLoc.x + ','
                            + parentParticleMeanLoc.y;
                        var parentLocLink = '<a name="'
                            + parentLocLinkName
                            + '" title="'
                            + parentCoordPair
                            + '" style="color : black" href="javascript:void(0)">mean</a>';
                        parentList.append("<li>Parent:" + parentLocLink + ', '
                            + parentEdgeLink + "</li>");

                        var parentStateMean = createMatrixString(particleData.parent.infResults.stateMean);
                        parentList.append("<li>state=" + parentStateMean
                            + "</li>");

                        var stateCov = createMatrixString(particleData.parent.infResults.stateCovariance);
                        parentList.append('<li>stateCov=' + stateCov + '</li>');
                        
                        var parentPathName = getPathName(particleData.parent.infResults.pathSegmentIds);
                        parentList.append('<li>path=' + parentPathName + '</li>');

                        createHoverPointLink(parentLocLinkName,
                            parentParticleMeanLoc);

                        if (parentEdgeId != null) {
                          createHoverLineLink(parentEdgeLinkName, parentEdgeId);
                        }

                      }

                      particleNumber++;
                    });
          });
}

function createHoverLineLink(linkName, edgeId) {
  if (edgeId < 0)
    return;
  var edgeLinkJName = 'a[name=' + linkName + ']';
  $(edgeLinkJName).data("edgeId", edgeId);
  $(edgeLinkJName).hover(function() {
    var localEdgeId = $(this).data("edgeId");
    var edge = this.edge;
    if (edge == null) {
      edge = drawEdge(localEdgeId, null, EdgeType.ADDED);
      this.edge = edge;
    } else {
      edgeGroup.addLayer(edge);
      addedGroup.addLayer(edge);
      map.invalidateSize();
    }
  }, function() {
    var edge = this.edge;
    if (edge != null) {
      edgeGroup.removeLayer(edge);
      addedGroup.removeLayer(edge);
      map.invalidateSize();
    }
  });
}

function createHoverPointLink(linkName, loc) {
  var locLinkJName = 'a[name=' + linkName + ']';
  $(locLinkJName).data("loc", loc);
  $(locLinkJName).hover(function() {

    var localLoc = $(this).data("loc");
    var marker = this.marker;
    if (marker == null) {
      marker = drawCoords(localLoc.x, localLoc.y, null, false);
      this.marker = marker;
    } else {
      pointsGroup.addLayer(marker);
      addedGroup.addLayer(marker);
      map.invalidateSize();
    }
  }, function() {
    var marker = this.marker;
    if (marker != null) {
      pointsGroup.removeLayer(marker);
      addedGroup.removeLayer(marker);
      map.invalidateSize();
    }
  });
}

function renderGraph() {
  paths = {};
  if (lines[i].infResults) {
    var pathList = jQuery("#paths");
    pathList.empty();

    var emptyOption = jQuery('<option id="none">none</option>');
    var startPointsOption = jQuery('<option id="startPoints">startPoints</option>');
    var endPointsOption = jQuery('<option id="endPoints">endPoints</option>');
    pathList.append(emptyOption);
    pathList.append(startPointsOption);
    pathList.append(endPointsOption);

    // jQuery.each(data.routes, function(_, routeId) {
    // var option = jQuery("<option>" + routeId + "</option>");
    // option.attr("value", routeId);
    // routeList.append(option);
    // });
    //
    // routeList.change(initBlockList);

    if (lines[i].infResults.evaluatedPaths.length > 0) {
      var evaledPaths = lines[i].infResults.evaluatedPaths;
      // paths
      for ( var k in evaledPaths) {

        // FIXME finish
        var pathName = 'path' + k;
        var pathStr = evaledPaths[k].pathEdgeIds.toString();
        var option = jQuery('<option id=' + pathName + '>path' + k + ':'
            + pathStr + '</option>');
        option.attr("value", pathName);
        option.data("path", evaledPaths[k]);
        pathList.append(option);
        paths[pathStr] = pathName;
      }
    }

    pathList.change(function() {
      // edgeGroup.clearLayers();
      evaluatedGroup.clearLayers();
      // segments
      $("select option:selected").each(function() {
        var pathName = this.value;
        if (pathName !== "none" 
          && pathName !== "startPoints"
          && pathName !== "endPoints" ) {
          var path = $('#' + pathName).data('path');
          var pathSegmentInfo = new Array();
          for ( var l in path.pathEdgeIds) {
            var idVelPair = new Array(path.pathEdgeIds[l], 0);
            pathSegmentInfo.push(idVelPair);
            // drawLine(idVelPair[0], idVelPair[1], EdgeType.EVALUATED);
          }
          renderPath(pathSegmentInfo, path.direction, EdgeType.EVALUATED);
        } else if (pathName === "startPoints") {
          var points = {};
          $.each(paths, function(key, value) {
            var path = $('#' + value).data('path');
            if (path.startVertex != null)
              points[path.startVertex.x + "," + path.startVertex.y] = [path.startVertex.x, path.startVertex.y];
          });
          $.each(points, function(key, value) {
            evaluatedGroup.addLayer(drawCoords(value[0], value[1], null, null));
          });
        } else if (pathName === "endPoints") {
          var points = {};
          $.each(paths, function(key, value) {
            var path = $('#' + value).data('path');
            if (path.endVertex != null)
              points[path.endVertex.x + "," + path.endVertex.y] = [path.endVertex.x, path.endVertex.y];
          });
          $.each(points, function(key, value) {
            evaluatedGroup.addLayer(drawCoords(value[0], value[1], null, null));
          });
        }
      });
      map.invalidateSize();
    });

    renderPath(lines[i].infResults.pathSegmentIds, lines[i].infResults.pathDirection, EdgeType.INFERRED);

  }

  renderParticles();

  if (lines[i].actualResults) {
    renderPath(lines[i].actualResults.pathSegmentIds, lines[i].actualResults.pathDirection, EdgeType.ACTUAL);
    // for ( var j in lines[i].actualResults.pathSegmentIds) {
    // var segmentInfo = lines[i].actualResults.pathSegmentIds[j];
    // drawLine(segmentInfo[0], segmentInfo[1], EdgeType.ACTUAL);
    // }
  }
}

function renderPath(pathSegmentIds, pathDirection, edgeType) {
  var color;
  var weight = 5;
  var opacity = 0.7;

  var groupType;
  if (edgeType == EdgeType.INFERRED) {
    color = "red";
    weight = 10;
    opacity = 0.3;
    groupType = inferredGroup;
  } else if (edgeType == EdgeType.ACTUAL) {
    color = "black";
    weight = 2;
    opacity = 1.0;
    groupType = actualGroup;
  } else if (edgeType == EdgeType.EVALUATED) {
    color = "blue";
    weight = 20;
    opacity = 0.2;
    groupType = evaluatedGroup;
  } else if (edgeType == EdgeType.ADDED) {
    color = "green";
    weight = 20;
    opacity = 0.2;
    groupType = addedGroup;
  }

  var segmentIds = $.extend(true, [], pathSegmentIds);
  if (pathDirection < 0) {
    segmentIds.reverse();
  }
    
  var latLngs = new Array();
  var justIds = new Array();
  for ( var j in segmentIds) {
    var segmentInfo = segmentIds[j];
    if (segmentInfo.length == 2 && segmentInfo[0] > -1) {
      justIds.push(segmentInfo[0]);
      $.ajax({
        url : '/api/segment?segmentId=' + segmentInfo[0],
        dataType : 'json',
        async : false,
        success : function(data) {
          for ( var k in data.geom.coordinates) {
            latLngs.push(new L.LatLng(data.geom.coordinates[k][1],
                data.geom.coordinates[k][0]));
          }
        }
      });
    }
  }

  var polyline = new L.Polyline(latLngs, {
    color : color,
    weight : weight,
    opacity : opacity
  });

  polyline.bindPopup(justIds.toString());

  edgeGroup.addLayer(polyline);
  groupType.addLayer(polyline);
  map.invalidateSize();

  return polyline;
}

L.Marker.Compass = L.Marker
    .extend({
      _reset : function() {
        var pos = this._map.latLngToLayerPoint(this._latlng).round();

        L.DomUtil.setPosition(this._icon, pos);
        if (this._shadow) {
          L.DomUtil.setPosition(this._shadow, pos);
        }

        if (this.options.iconAngle) {
          this._icon.style.WebkitTransform = this._icon.style.WebkitTransform
              + ' rotate(' + this.options.iconAngle + 'deg)';
          this._icon.style.MozTransform = 'rotate(' + this.options.iconAngle
              + 'deg)';
          this._icon.style.MsTransform = 'rotate(' + this.options.iconAngle
              + 'deg)';
          this._icon.style.OTransform = 'rotate(' + this.options.iconAngle
              + 'deg)';
        }

        this._icon.style.zIndex = pos.y;
      },

      setIconAngle : function(iconAngle) {

        if (this._map) {
          this._removeIcon();
        }

        this.options.iconAngle = iconAngle;

        if (this._map) {
          this._initIcon();
          this._reset();
        }
      }

    });

function getColor(f) {
  var n = Math.round(100 * f);

  var red = (255 * n) / 100;
  var green = (255 * (100 - n)) / 100;
  var blue = 0;

  var rgb = blue | (green << 8) | (red << 16);

  return rgb.toString(16);
}
