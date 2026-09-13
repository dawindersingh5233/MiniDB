package com.minidb.storage;

import com.minidb.schema.*;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class test {
    public static void main(String args[]){
        ClusteredIndex index = new ClusteredIndex(1);
        LeafNode leaf = index.findLeaf(900);
        System.out.println("Key count: "+leaf.getKeyCount());
        System.out.println("PageId: "+leaf.getPageId());
        System.out.println("Parent PageId: "+leaf.getParentPageId());
    }

    public static void init(){
        LeafNode root = new LeafNode();
        root.setPageId(1);

        DiskManager disk = DiskManager.getInstance();

        try{
            disk.writePage(1, root);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static int testInsert(){
        ClusteredIndex clusteredIndex = new ClusteredIndex(1);

        for(int i = 1; i < 1000; i++){
            ByteBuffer row = getData(i);
            clusteredIndex.insert(i, row);
        }

        int rootId = clusteredIndex.getRootPageId();

        System.out.println("New root id is: "+rootId);
        System.out.println("Done !");

        return rootId;
    }

    public static void testRead(int rootId, int key){
        ClusteredIndex clusteredIndex = new ClusteredIndex(rootId);

        ByteBuffer data = clusteredIndex.find(key);
        printData(data);
    }

    public static int testDelete(int rootId){
        ClusteredIndex clusteredIndex = new ClusteredIndex(rootId);

        for(int i = 200; i < 850; i++){
            clusteredIndex.delete(i);
        }

        int newRootId = clusteredIndex.getRootPageId();
        System.out.println("New Root Id after deletion: "+newRootId);
        System.out.println("Done !");

        return newRootId;
    }

    public static ByteBuffer getData(int key){
        Schema schema = getSchema();
        Random rand = new Random();

        List<Value> value = new ArrayList<>();
        value.add(Value.valueOf(key));
        value.add(Value.valueOf("Dawinder - "+key));
        value.add(Value.valueOf(rand.nextInt(100)));

        Tuple tuple = new Tuple(value, schema);

        return ByteBuffer.wrap(tuple.getData());
    }

    public static void flushAll(){
        BufferPoolManager buffer = BufferPoolManager.getInstance();
        buffer.flushAll();
    }

    public static void printData(ByteBuffer data){
        Schema schema = getSchema();
        Tuple tuple = new Tuple(data.array(), schema);

        for(int i = 0; i < schema.getColumnCount(); i++){
            System.out.print(tuple.getValue(i)+ " ");
        }
        System.out.println();
    }

    public static Schema getSchema(){
        List<Column> columns = new ArrayList<>();
        columns.add(new Column("id", TypeId.INTEGER));
        columns.add(new Column("name", TypeId.VARCHAR));
        columns.add(new Column("marks", TypeId.INTEGER));

        Schema schema = new Schema(columns, "id");

        return schema;
    }
}
