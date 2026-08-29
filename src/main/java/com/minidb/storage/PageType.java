package com.minidb.storage;

public class PageType {
    public static final byte INVALID = 0;
    public static final byte META = 1;
    public static final byte INDEX_INTERNAL = 2;
    public static final byte INDEX_LEAF = 3;
    public static final byte FREE = 4;
}
