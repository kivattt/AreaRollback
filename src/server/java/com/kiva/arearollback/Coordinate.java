package com.kiva.arearollback;

import java.util.Objects;

public class Coordinate {
    public int x, y, z;

    @Override
    public String toString() {
        return "x:" + x + ", y:" + y + ", z:" + z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Coordinate that = (Coordinate) o;
        return that.x == x && that.y == y && that.z == z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
