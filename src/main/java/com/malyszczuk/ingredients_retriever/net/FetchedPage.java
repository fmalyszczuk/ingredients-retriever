package com.malyszczuk.ingredients_retriever.net;

import java.net.URI;
import java.nio.charset.Charset;

/** A downloaded web page. {@code charset} is null when the server didn't say (the parser then detects it). */
public record FetchedPage(URI finalUrl, byte[] body, Charset charset) {
}
