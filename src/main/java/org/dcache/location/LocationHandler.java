/**
 * Copyright (C) 2013 dCache.org <support@dcache.org>
 *
 * This file is part of dcache-location-service.
 *
 * dcache-location-service is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * dcache-location-service is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with dcache-location-service.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.dcache.location;

import com.google.common.base.Joiner;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.EnumSet;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.FileNotFoundCacheException;
import diskCacheV111.util.PnfsHandler;
import diskCacheV111.util.TimeoutCacheException;

import org.dcache.namespace.FileAttribute;

import static com.google.common.collect.Iterables.transform;

public class LocationHandler extends IdleStateAwareChannelHandler
{
    protected static final String CRLF = "\r\n";
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationHandler.class);

    private PnfsHandler pnfs;
    private Translator translator;

    public LocationHandler(PnfsHandler pnfs, Translator translator)
    {
        this.pnfs = pnfs;
        this.translator = translator;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent event)
    {
        try {
            if (!(event.getMessage() instanceof HttpRequest)) {
                return;
            }
            HttpRequest request = (HttpRequest) event.getMessage();
            if (request.getMethod() != HttpMethod.GET) {
                unsupported(ctx, event);
                return;
            }

            String path = new URI(request.getUri()).getPath();
            Collection<String> locations = pnfs.getFileAttributes(path,
                    EnumSet.of(FileAttribute.LOCATIONS)).getLocations();
            String content = Joiner.on(CRLF).join(transform(locations, translator.translateFunction())) + CRLF;

            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.setContent(ChannelBuffers.copiedBuffer(content, CharsetUtil.UTF_8));
            HttpHeaders.setContentLength(response, response.getContent().readableBytes());
            ctx.getChannel().write(response);
        } catch (URISyntaxException e) {
            sendHTTPError(ctx, HttpResponseStatus.BAD_REQUEST, e.getMessage());
        } catch (FileNotFoundCacheException e) {
            sendHTTPError(ctx, HttpResponseStatus.NOT_FOUND, e.getMessage());
        } catch (TimeoutCacheException e) {
            sendHTTPError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (CacheException e) {
            sendHTTPError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.error("Location service failed. Please report to behrmann@nordu.net", e);
            sendHTTPError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    protected static ChannelFuture sendHTTPError(
            ChannelHandlerContext ctx,
            HttpResponseStatus statusCode,
            String message)
    {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, statusCode);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.setContent(ChannelBuffers.copiedBuffer(message + CRLF, CharsetUtil.UTF_8));
        HttpHeaders.setContentLength(response, response.getContent().readableBytes());
        return ctx.getChannel().write(response);
    }

    protected static ChannelFuture unsupported(
            ChannelHandlerContext ctx, MessageEvent event)
    {
        return sendHTTPError(ctx, HttpResponseStatus.NOT_IMPLEMENTED,
                "The requested operation is not supported");
    }
}
