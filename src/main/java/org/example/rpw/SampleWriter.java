package org.example.rpw;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

public class SampleWriter<T> implements ItemWriter<T> {
    private int counter = 0;

    @Override
    public void write(Chunk<? extends T> items) throws Exception {
        int current = counter;
        counter += items.size();
        System.out.println("writing item : current = " + current + " counter = " + counter);
        if (current < 3 && (counter >= 2 || counter >= 3)) {
//            throw new IllegalStateException("Temporary error");
//            throw new IllegalArgumentException("Temporary error: ${current}");
        }
        System.out.println("About to write chunk: " + items);
        for (T item : items) {
            System.out.println("writing item = " + item);
        }
    }

    /**
     * @return number of times {@link #write(Chunk)} method was called.
     */
    public int getCounter() {
        return counter;
    }
}
