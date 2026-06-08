package com.eRez.tests.data;

import com.eRez.tests.database.document.NodeDocument;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
public class CacheData {
    private List<NodeDocument> nodes = new ArrayList<>();
}
