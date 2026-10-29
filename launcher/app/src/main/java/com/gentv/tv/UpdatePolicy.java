package com.gentv.tv;

/** Mandatory policy applies only to a newer, consistent release. */
final class UpdatePolicy {
    static boolean required(long current, long latest, long minimum, boolean force) {
        if (latest <= 0 || minimum < 0 || minimum > latest) throw new IllegalArgumentException("Invalid release policy");
        return latest > current && (force || current < minimum);
    }
}
