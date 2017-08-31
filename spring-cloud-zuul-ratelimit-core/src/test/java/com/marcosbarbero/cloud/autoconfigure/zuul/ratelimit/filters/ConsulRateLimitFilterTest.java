package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.Rate;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.ConsulRateLimiter;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Marcos Barbero
 * @since 2017-08-28
 */
public class ConsulRateLimitFilterTest extends BaseRateLimitFilterTest {

    private ConsulClient consulClient = mock(ConsulClient.class);
    private ObjectMapper objectMapper = new ObjectMapper();

    private Rate rate(long remaining) {
        return new Rate("key", remaining, 100L, new Date(System.currentTimeMillis() + SECONDS.toMillis(2)));
    }

    @Before
    @Override
    public void setUp() {
        this.setRateLimiter(new ConsulRateLimiter(this.consulClient, this.objectMapper));
        super.setUp();
    }

    @Test
    @Override
    @SuppressWarnings("unchecked")
    public void testRateLimitExceedCapacity() throws Exception {
        Response<GetValue> response = mock(Response.class);
        GetValue getValue = mock(GetValue.class);
        when(this.consulClient.getKVValue(anyString())).thenReturn(response);
        when(response.getValue()).thenReturn(getValue);
        when(getValue.getDecodedValue()).thenReturn(this.objectMapper.writeValueAsString(this.rate(-1)));
        super.testRateLimitExceedCapacity();
    }

    @Test
    @Override
    @SuppressWarnings("unchecked")
    public void testRateLimit() throws Exception {
        Response<GetValue> response = mock(Response.class);
        GetValue getValue = mock(GetValue.class);
        when(this.consulClient.getKVValue(anyString())).thenReturn(response);
        when(response.getValue()).thenReturn(getValue);
        when(getValue.getDecodedValue()).thenReturn(this.objectMapper.writeValueAsString(this.rate(1)));

        this.request.setRequestURI("/serviceA");
        this.request.setRemoteAddr("10.0.0.100");

        assertTrue(this.filter.shouldFilter());

        for (int i = 0; i < 2; i++) {
            this.filter.run();
        }

        String remaining = this.response.getHeader(RateLimitFilter.Headers.REMAINING);
        assertEquals("0", remaining);

        TimeUnit.SECONDS.sleep(2);

        when(getValue.getDecodedValue()).thenReturn(this.objectMapper.writeValueAsString(this.rate(2)));
        this.filter.run();
        remaining = this.response.getHeader(RateLimitFilter.Headers.REMAINING);
        assertEquals("1", remaining);
    }
}
