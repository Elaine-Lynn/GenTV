package com.gentv.tv;

/** Exactly one immediate backup attempt after a failed primary request or validation. */
final class UpdateFailover {
    interface Source<T> { T load() throws Exception; }
    static final class Failure extends Exception {
        final Exception primary;
        Failure(Exception primary, Exception backup) { super("Primary and backup update checks failed", backup); this.primary = primary; }
    }
    static <T> T load(Source<T> primary, Source<T> backup) throws Exception {
        try { return primary.load(); }
        catch (Exception first) {
            try { return backup.load(); }
            catch (Exception second) { throw new Failure(first, second); }
        }
    }
}
