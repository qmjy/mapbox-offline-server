package com.example;


import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;

/**
 * 本类提供经纬度互换
 */
public class CoordinateSwapper implements CoordinateSequenceFilter {
    private boolean done = false;

    @Override
    public void filter(CoordinateSequence seq, int i) {
        double x = seq.getX(i);
        double y = seq.getY(i);
        seq.setOrdinate(i, 0, y); // 将经度设置为纬度值
        seq.setOrdinate(i, 1, x); // 将纬度设置为经度值
        if (i == seq.size() - 1) {
            done = true;
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isGeometryChanged() {
        return true;
    }

    public static void swapCoordinates(Geometry geom) {
        geom.apply(new CoordinateSwapper());
    }
}