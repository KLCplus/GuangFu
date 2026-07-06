package com.example.pvplatform.module.pvdata.parser;

import java.io.IOException;
import java.io.InputStream;

public interface PvDataFileParser {
    boolean supports(String extension);

    ParsedPvDataFile parse(InputStream input, int maxRows) throws IOException;
}
