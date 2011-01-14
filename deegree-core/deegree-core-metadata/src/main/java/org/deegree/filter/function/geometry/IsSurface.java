//$HeadURL$
package org.deegree.filter.function.geometry;

import java.util.List;

import org.deegree.commons.tom.TypedObjectNode;
import org.deegree.commons.tom.primitive.PrimitiveValue;
import org.deegree.filter.Expression;
import org.deegree.filter.FilterEvaluationException;
import org.deegree.filter.XPathEvaluator;
import org.deegree.filter.expression.Function;
import org.deegree.filter.function.FunctionProvider;
import org.deegree.filter.utils.FilterUtils;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.multi.MultiPolygon;
import org.deegree.geometry.multi.MultiSurface;
import org.deegree.geometry.primitive.Surface;

/**
 * Returns no value in case the argument expression evaluates to no value, or multiple values, or the value can not be
 * interpreted as a geometry.
 * 
 * @author <a href="mailto:schmitz@lat-lon.de">Andreas Schmitz</a>
 * @author last edited by: $Author$
 * 
 * @version $Revision$, $Date$
 */
public class IsSurface implements FunctionProvider {

    private static final String NAME = "IsSurface";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getArgCount() {
        return 1;
    }

    @Override
    public Function create( List<Expression> params ) {
        if ( params.size() != 1 ) {
            throw new IllegalArgumentException( NAME + " requires exactly one parameter." );
        }
        return new Function( NAME, params ) {
            @Override
            public <T> TypedObjectNode[] evaluate( T obj, XPathEvaluator<T> xpathEvaluator )
                                    throws FilterEvaluationException {
                TypedObjectNode[] vals = getParams()[0].evaluate( obj, xpathEvaluator );

                if ( vals.length != 1 ) {
                    throw new FilterEvaluationException( "The " + NAME + " function's first argument must evaluate"
                                                         + " to exactly one value." );
                }
                Geometry geom = FilterUtils.getGeometryValue( vals[0] );

                if ( geom == null ) {
                    throw new FilterEvaluationException( "The " + NAME + " function's first argument did"
                                                         + " not evaluate to a geometry." );
                }

                // TODO is handling of multi geometries like this ok?
                boolean isSurface = geom instanceof Surface || geom instanceof MultiPolygon
                                    || geom instanceof MultiSurface;
                return new TypedObjectNode[] { new PrimitiveValue( Boolean.toString( isSurface ) ) };
            }
        };
    }
}