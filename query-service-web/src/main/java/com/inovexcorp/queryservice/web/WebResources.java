package com.inovexcorp.queryservice.web;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardContextSelect;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardResource;

@Component(service = WebResources.class, immediate = true)
@HttpWhiteboardResource(pattern = "/*", prefix = "/build")
@HttpWhiteboardContextSelect("(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "="
  + QueryServiceContextHelper.NAME + ")")
public class WebResources {

}
