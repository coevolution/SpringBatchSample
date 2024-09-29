package org.example.rpw;

import org.springframework.batch.item.ItemProcessor;

public class SampleProcessor implements ItemProcessor {
    @Override
    public Object process(Object item) throws Exception {
        if (item.equals(5)) {
            System.out.println("Throwing exception on item " + item);
            throw new IllegalArgumentException("Unable to process 5");
        }
        System.out.println("processing item = " + item);
        return item;
    }
}
