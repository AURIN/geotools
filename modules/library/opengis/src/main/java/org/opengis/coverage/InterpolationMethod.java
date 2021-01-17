/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2011, Open Source Geospatial Foundation (OSGeo)
 *    (C) 2005 Open Geospatial Consortium Inc.
 *
 *    All Rights Reserved. http://www.opengis.org/legal/
 */
package org.opengis.coverage;

import static org.opengis.annotation.Obligation.CONDITIONAL;
import static org.opengis.annotation.Specification.ISO_19123;

import java.util.ArrayList;
import java.util.List;
import org.opengis.annotation.UML;
import org.opengis.util.CodeList;

/**
 * A list of codes that identify interpolation methods that may be used for evaluating {@linkplain
 * ContinuousCoverage continuous coverages}. Evaluation of a continuous coverage involves
 * interpolation between known feature attribute values associated with geometric objects in the
 * domain of the {@linkplain DiscreteCoverage discrete coverage} that is provided as control for the
 * continuous coverage. This code list includes 9 interpolation methods. Each is used in the context
 * of specified geometric configurations (table below). Since {@code InterpolationMethod} is a
 * {@code CodeList}, it may be extended in an application schema that specifies additional
 * interpolation methods.
 *
 * <p>
 *
 * <table border="1">
 *   <tr><th>Method</th>           <th>Coverage Type</th> <th>Value object dimension</th></tr>
 *   <tr><td>Nearest Neighbour</td><td>Any</td>                             <td>Any</td></tr>
 *   <tr><td>Linear</td>           <td>Segmented Curve</td>                 <td>1</td></tr>
 *   <tr><td>Quadratic</td>        <td>Segmented Curve</td>                 <td>1</td></tr>
 *   <tr><td>Cubic</td>            <td>Segmented Curve</td>                 <td>1</td></tr>
 *   <tr><td>Bilinear</td>         <td>Quadrilateral Grid</td>              <td>2</td></tr>
 *   <tr><td>Biquadratic</td>      <td>Quadrilateral Grid</td>              <td>2</td></tr>
 *   <tr><td>Bicubic</td>          <td>Quadrilateral Grid</td>              <td>2</td></tr>
 *   <tr><td>Lost Area</td>        <td>Thiessen Polygon, Hexagonal Grid</td><td>2</td></tr>
 *   <tr><td>Barycentric</td>      <td>TIN</td>                             <td>2</td></tr>
 * </table>
 *
 * @version ISO 19123:2004
 * @author Martin Desruisseaux (IRD)
 * @since GeoAPI 2.1
 */
@UML(identifier = "CV_InterpolationMethod", specification = ISO_19123)
public class InterpolationMethod extends CodeList<InterpolationMethod> {
    /** Serial number for compatibility with different versions. */
    private static final long serialVersionUID = -4289541167757079847L;

    /** List of all enumerations of this type. Must be declared before any enum declaration. */
    private static final List<InterpolationMethod> VALUES = new ArrayList<>(9);

    /**
     * Generates a feature attribute value at a direct position by assigning it the feature
     * attribute value associated with the nearest domain object in the domain of the coverage.
     * Nearest neighbour interpolation extends a discrete coverage to a step function defined on the
     * convex hull of the domain objects in the domain of the coverage. Nearest neighbour
     * interpolation is the only interpolation method described in ISO 19123 that can be used to
     * interpolate attributes that have nominal or ordinal values.
     *
     * <p><B>NOTE:</B> In the case of a discrete point coverage, the "steps" of the step function
     * are the Thiessen polygons generated by the set of points in the domain of the coverage.
     */
    @UML(identifier = "Nearest neighbour", obligation = CONDITIONAL, specification = ISO_19123)
    public static final InterpolationMethod NEAREST_NEIGHBOUR =
            new InterpolationMethod("NEAREST_NEIGHBOUR");

    /**
     * Interpolation based on the assumption that feature attribute values vary in proportion to
     * distance along a value segment.
     *
     * <p>
     *
     * <blockquote>
     *
     * <var>v</var> = <var>a</var> + <var>b</var><var>x</var>
     *
     * </blockquote>
     *
     * <p>Linear interpolation may be used to interpolate feature attribute values along a line
     * segment connecting any two point value pairs. It may also be used to interpolate feature
     * attribute values at positions along a curve of any form, if the positions are described by
     * values of an arc-length parameter.
     *
     * <p>Given two point value pairs (<var>p</var><sub>s</sub>, </var>v</var><sub>s</sub>) and
     * (<var>p</var><sub>t</sub>, <var>v</var><sub>t</sub>), where <var>p</var><sub>s</sub> is the
     * start point and <var>p</var><sub>t</sub> is the end point of a value segment, and
     * <var>v</var><sub>s</sub> and <var>v</var><sub>t</sub> are the feature attribute values
     * associated with those points, the feature attribute value <var>v</var><sub>i</sub> associated
     * with the direct position <var>p</var><sub>i</sub> is:
     *
     * <p>
     *
     * <blockquote>
     *
     * <var>v</var><sub>i</sub> = <var>v</var><sub>s</sub> + (<var>v</var><sub>t</sub> -
     * <var>v</var><sub>s</sub>) ((<var>p</var><sub>i</sub> -
     * <var>p</var><sub>s</sub>)/(<var>p</var><sub>t</sub> - <var>p<var><sub>s</sub>))
     *
     * </blockquote>
     */
    @UML(identifier = "Linear interpolation", obligation = CONDITIONAL, specification = ISO_19123)
    public static final InterpolationMethod LINEAR = new InterpolationMethod("LINEAR");

    /**
     * Interpolation based on the assumption that feature attribute values vary as a quadratic
     * function of distance along a value segment.
     *
     * <p>
     *
     * <blockquote>
     *
     * <var>v</var> = <var>a</var> + <var>b</var><var>x</var> + <var>c</var><var>x</var><sup>2</sup>
     *
     * </blockquote>
     *
     * <p>where <var>a</var> is the value of a feature attribute at the start of a value segment and
     * <var>v</var> is the value of a feature attribute at distance <var>x</var> along the curve
     * from the start. Three point value pairs are needed to provide control values for calculating
     * the coefficients of the function.
     */
    @UML(
        identifier = "Quadratic interpolation",
        obligation = CONDITIONAL,
        specification = ISO_19123
    )
    public static final InterpolationMethod QUADRATIC = new InterpolationMethod("QUADRATIC");

    /**
     * Interpolation based on the assumption that feature attribute values vary as a cubic function
     * of distance along a value segment.
     *
     * <p>
     *
     * <blockquote>
     *
     * <var>v</var> = <var>a</var> + <var>b</var><var>x</var> + <var>c</var><var>x</var><sup>2</sup>
     * + <var>d</var><var>x</var><sup>3</sup>
     *
     * </blockquote>
     *
     * <p>where <var>a</var> is the value of a feature attribute at the start of a value segment and
     * <var>v</var> is the value of a feature attribute at distance <var>x</var> along the curve
     * from the start. Four point value pairs are needed to provide control values for calculating
     * the coefficients of the function.
     */
    @UML(identifier = "Cubic interpolation", obligation = CONDITIONAL, specification = ISO_19123)
    public static final InterpolationMethod CUBIC = new InterpolationMethod("CUBIC");

    /**
     * Interpolation based on the assumption that feature attribute values vary as a bilinear
     * function of position within the grid cell.
     */
    @UML(identifier = "Bilinear interpolation", obligation = CONDITIONAL, specification = ISO_19123)
    public static final InterpolationMethod BILINEAR = new InterpolationMethod("BILINEAR");

    /**
     * Interpolation based on the assumption that feature attribute values vary as a biquadratic
     * function of position within the grid cell.
     */
    @UML(
        identifier = "Biquadratic interpolation",
        obligation = CONDITIONAL,
        specification = ISO_19123
    )
    public static final InterpolationMethod BIQUADRATIC = new InterpolationMethod("BIQUADRATIC");

    /**
     * Interpolation based on the assumption that feature attribute values vary as a bicubic
     * function of position within the grid cell.
     */
    @UML(identifier = "Bicubic interpolation", obligation = CONDITIONAL, specification = ISO_19123)
    public static final InterpolationMethod BICUBIC = new InterpolationMethod("BICUBIC");

    /** Lost area interpolation. */
    @UML(
        identifier = "Lost area interpolation",
        obligation = CONDITIONAL,
        specification = ISO_19123
    )
    public static final InterpolationMethod LOST_AREA = new InterpolationMethod("LOST_AREA");

    /** Barycentric interpolation. */
    @UML(
        identifier = "Barycentric interpolation",
        obligation = CONDITIONAL,
        specification = ISO_19123
    )
    public static final InterpolationMethod BARYCENTRIC = new InterpolationMethod("BARYCENTRIC");

    /**
     * Constructs an enum with the given name. The new enum is automatically added to the list
     * returned by {@link #values}.
     *
     * @param name The enum name. This name must not be in use by an other enum of this type.
     */
    private InterpolationMethod(final String name) {
        super(name, VALUES);
    }

    /**
     * Returns the list of {@code InterpolationMethod}s.
     *
     * @return The list of codes declared in the current JVM.
     */
    public static InterpolationMethod[] values() {
        synchronized (VALUES) {
            return VALUES.toArray(new InterpolationMethod[VALUES.size()]);
        }
    }

    /** Returns the list of enumerations of the same kind than this enum. */
    public InterpolationMethod[] family() {
        return values();
    }

    /**
     * Returns the interpolation method that matches the given string, or returns a new one if none
     * match it.
     *
     * @param code The name of the code to fetch or to create.
     * @return A code matching the given name.
     */
    public static InterpolationMethod valueOf(String code) {
        return valueOf(InterpolationMethod.class, code);
    }
}
