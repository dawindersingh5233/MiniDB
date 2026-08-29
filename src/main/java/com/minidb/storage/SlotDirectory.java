package com.minidb.storage;

import java.util.ArrayList;
import java.util.List;

public class SlotDirectory{
    private List<Slot> slots;

    public SlotDirectory(){
        this.slots = new ArrayList<>();
    }

    public void addSlot(Slot slot){
        this.slots.add(slot);
    }

    public void addSlotAtPos(int pos, Slot slot){
        this.slots.add(pos, slot);
    }

    public void addSlot(short offset, short length){
        this.slots.add(new Slot(offset, length));
    }

    public void addSlotAtPos(int pos, short offset, short length){
        this.slots.add(pos, new Slot(offset, length));
    }

    public Slot get(int index){
        return this.slots.get(index);
    }

    public void removeSlot(int index){
        this.slots.remove(index);
    }

    public void setAsTombstone(int index){
        this.slots.get(index).setAsTombstone();
    }

    public List<Slot> getSlots(){
        return List.copyOf(this.slots);
    }
}
