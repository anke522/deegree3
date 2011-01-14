<?xml version="1.0" encoding="UTF-8"?>
<Curve gml:id="C1" xmlns="http://www.opengis.net/gml" xmlns:gml="http://www.opengis.net/gml" srsName="EPSG:4326" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.opengis.net/gml http://schemas.opengis.net/gml/3.1.1/base/geometryPrimitives.xsd">	
	<segments>
		<Arc interpolation="circularArc3Points">
			<posList srsName="EPSG:4326">2 0 0 2 -2 0</posList>
		</Arc>
		<!--  invalid, because segment end point does not coincide with next segment's start point-->
		<LineStringSegment interpolation="linear">
	        <posList srsName="EPSG:4326">-2.001 0 0 -2 2 0</posList>
        </LineStringSegment>
	</segments>
</Curve>
