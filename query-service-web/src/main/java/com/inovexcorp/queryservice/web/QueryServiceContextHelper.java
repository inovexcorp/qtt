package com.inovexcorp.queryservice.web;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardContext;

import java.net.URL;
import java.util.Set;

@Component(service = ServletContextHelper.class, scope = ServiceScope.BUNDLE)
@HttpWhiteboardContext(name = QueryServiceContextHelper.NAME, path = "/")
public class QueryServiceContextHelper extends ServletContextHelper {
  public static final String NAME = "queryservice";

  private ServletContextHelper delegatee;

  @Activate
  private void activate(final ComponentContext ctx) {
    delegatee = new ServletContextHelper(ctx.getUsingBundle()) {};
  }

  @Override
  public URL getResource(String name) {
    if ("build/".equals(name) || !name.matches("^.*\\.[^\\\\]+$")) {
      return delegatee.getResource("build/index.html");
    }
    return delegatee.getResource(name);
  }

  @Override
  public String getMimeType(String name) {
    return delegatee.getMimeType(name);
  }

  @Override
  public Set<String> getResourcePaths(String path) {
    return delegatee.getResourcePaths(path);
  }

  @Override
  public String getRealPath(String path) {
    return delegatee.getRealPath(path);
  }
}

