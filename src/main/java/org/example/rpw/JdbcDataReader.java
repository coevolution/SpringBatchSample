package org.example.rpw;

import org.springframework.batch.item.support.ListItemReader;

import java.util.List;

public class JdbcDataReader extends ListItemReader {

    public JdbcDataReader(List list) {
        super(list);
    }
}
