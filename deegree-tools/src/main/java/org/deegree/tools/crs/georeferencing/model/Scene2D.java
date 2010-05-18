//$HeadURL$
/*----------------------------------------------------------------------------
 This file is part of deegree, http://deegree.org/
 Copyright (C) 2001-2009 by:
 - Department of Geography, University of Bonn -
 and
 - lat/lon GmbH -

 This library is free software; you can redistribute it and/or modify it under
 the terms of the GNU Lesser General Public License as published by the Free
 Software Foundation; either version 2.1 of the License, or (at your option)
 any later version.
 This library is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 details.
 You should have received a copy of the GNU Lesser General Public License
 along with this library; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

 Contact information:

 lat/lon GmbH
 Aennchenstr. 19, 53177 Bonn
 Germany
 http://lat-lon.de/

 Department of Geography, University of Bonn
 Prof. Dr. Klaus Greve
 Postfach 1147, 53001 Bonn
 Germany
 http://www.geographie.uni-bonn.de/deegree/

 e-mail: info@deegree.org
 ----------------------------------------------------------------------------*/
package org.deegree.tools.crs.georeferencing.model;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.net.URL;

import javax.vecmath.Point2d;

import org.deegree.geometry.Envelope;
import org.deegree.geometry.GeometryFactory;

/**
 * Base interface for the model layer
 * 
 * @author <a href="mailto:thomas@lat-lon.de">Steffen Thomas</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public interface Scene2D {

    /**
     * Generates an image based on a rectangle in which the image has to be drawn
     * 
     * @param sceneBounds
     * @return
     */
    public BufferedImage generateImage( Rectangle sceneBounds );

    /**
     * Generates the predicted image for a faster repainting.
     * 
     * @param envelope
     * @return
     */
    public BufferedImage generatePredictedImage( Envelope envelope );

    /**
     * This should set the boundingbox for the image to draw.
     * 
     * @param change
     */
    public void changeImageBoundingbox( Point2d change );

    /**
     * 
     * @param change
     * @return
     */
    public void changePredictionBoundingbox( Point2d change );

    /**
     * returns the boundingbox of the image
     */
    public Envelope getImageBoundingbox();

    /**
     * Returns the requested image.
     * 
     * @return
     */
    public BufferedImage getRequestedImage();

    /**
     * Determines the boundingbox for the initialized request.
     * 
     * @param imageUrl
     * @param geometryFactory
     * @return
     */
    public Envelope determineRequestBoundingbox( URL imageUrl, GeometryFactory geometryFactory );

    /**
     * Returns the hole boundingbox
     * 
     * @return
     */
    public Envelope getRequestBoundingbox();

    /**
     * Returns the predicted boundinbox
     * 
     * @return
     */
    public Envelope getPredictionBoundingbox();

    /**
     * sets the window where everything is drawn into.
     * 
     * @param sightWindowBoundingbox
     */
    public void setSightWindowBoundingbox( Envelope sightWindowBoundingbox );

    /**
     * Resets all of the variables to initial value.
     */
    public void reset();

}
