package com.republicate.modality.webapp;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.republicate.modality.Model;
import com.republicate.modality.ModelRepository;
import com.republicate.modality.config.ConfigurationException;
import org.apache.velocity.tools.ToolContext;
import org.apache.velocity.tools.view.JeeConfig;
import org.apache.velocity.tools.view.ServletUtils;
import org.apache.velocity.tools.view.VelocityView;
import org.apache.velocity.tools.view.ViewContext;
import org.apache.velocity.util.ClassUtils;
import org.apache.velocity.util.ExtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

public class WebappModelProvider extends WebappModalityConfig
{
    protected static Logger logger = LoggerFactory.getLogger("model-configure");

    public static final String MODEL_ID = "auth.model.model_id";

    public WebappModelProvider(JeeConfig config)
    {
        super(config);
    }

    public Model getModel() throws ServletException
    {
        return getModel(true);
    }

    public Model getModel(boolean initialize) throws ServletException
    {
        if (model == null && initialize)
        {
            synchronized (this)
            {
                if (model == null)
                {
                    initialize();
                }
            }
        }
        return model;
    }

    public void configure() throws ServletException
    {
        super.configure();
        modelId = findConfigParameter(MODEL_ID);
    }

    //
    // Model Initialization
    // - application toolbox model
    // - ModelRepository toolbox
    //

    private void initialize() throws ServletException
    {
        // if the model has been initialized via a model tool,
        // make sure the model tool is ready
        model = initModelFromApplicationToolbox();
        if (model == null)
        {
            // No application toolbox, or nothing found within.
            // Just ask the repository.
            model = initModelFromRepository();

            // Still not available? Try to initialize it ourselves.
            if (model == null)
            {
                model = initNewModel();

                // otherwise bail out
                if (model == null)
                {
                    throw new RuntimeException("ModelAuthFilter: no model found" + (modelId == null ? "" : " for model id " + modelId));
                }
            }
        }
    }

    private Model initModelFromApplicationToolbox()
    {
        Model model = null;
        VelocityView view = ServletUtils.getVelocityView(getWebConfig());
        if (view.hasApplicationTools())
        {
            Set<String> modelKeys = new HashSet<>();
            Class modelToolClass = null;
            try
            {
                modelToolClass = ClassUtils.getClass("com.republicate.modality.tools.model.ModelTool");
            }
            catch (ClassNotFoundException cnfe) {}
            if (modelToolClass != null)
            {
                // search for a model in application tools
                for (Map.Entry<String, Class> entry : view.getApplicationToolbox().getToolClassMap().entrySet())
                {
                    if (modelToolClass.isAssignableFrom(entry.getValue()))
                    {
                        modelKeys.add(entry.getKey());
                    }
                }
            }
            String modelKey = null;

            if (modelId == null)
            {
                if (modelKeys.size() > 1)
                {
                    throw new RuntimeException("authentication filter cannot choose between several models, please specify " + MODEL_ID + " in configuration parameters");
                }
                else if (!modelKeys.isEmpty())
                {
                    modelId = modelKey = modelKeys.iterator().next();
                }
            }
            else
            {
                if (modelKeys.contains(modelId))
                {
                    modelKey = modelId;
                }
            }

            if (modelKey != null)
            {
                logger.info("Found model id '{}' in application toolbox", modelKey);

                // force model initialization
                view.createContext().get(modelKey);

                // get model
                model = ModelRepository.getModel(getWebConfig().getServletContext(), modelKey);
            }
        }
        return model;
    }

    private Model initModelFromRepository()
    {
        Model model = null;
        String[] ids =
            modelId == null ?
                new String[] { "default", "model" } :
                new String[] { modelId };
        for (String id : ids)
        {
            model = ModelRepository.getModel(id);
            if (model != null)
            {
                modelId = id;
                logger.info("Found model id '{}' in model repository", id);
                break;
            }
        }
        return model;
    }

    private Model initNewModel()
    {
        Model model = null;
        try
        {
            Map params = new HashMap();
            params.put(ViewContext.SERVLET_CONTEXT_KEY, getWebConfig().getServletContext());
            params.put(ToolContext.ENGINE_KEY, ServletUtils.getVelocityView(getWebConfig()).getVelocityEngine());
            model = new Model().configure(params);
            if (modelId == null)
            {
                model.initialize();
                modelId = model.getModelId();
            }
            else
            {
                model.initialize(modelId);
            }
            logger.info("Configured new model with model id '{}'", modelId);
        }
        catch (ConfigurationException ce)
        {
            logger.error("could not configure and initialize model", ce);
        }
        return model;
    }

    private String modelId = null;
    private Model model = null;
    
}