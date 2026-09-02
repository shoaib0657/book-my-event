package com.shoaib.bookmyevent.apigateway.config;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

@Component
final class OriginalContentTypeRequestHeadersFilter implements RequestHttpHeadersFilter, Ordered {

	@Override
	public HttpHeaders apply(HttpHeaders headers, ServerRequest request) {
		String originalContentType = request.servletRequest().getHeader(HttpHeaders.CONTENT_TYPE);
		if (originalContentType == null) {
			return headers;
		}

		HttpHeaders filtered = new HttpHeaders();
		filtered.addAll(headers);
		filtered.set(HttpHeaders.CONTENT_TYPE, originalContentType);
		return filtered;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
