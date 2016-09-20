package com.atc.simulator.Display;

import com.atc.simulator.Config.ApplicationConfig;
import com.atc.simulator.DebugDataFeed.DataPlaybackListener;
import com.atc.simulator.DebugDataFeed.DataPlaybackThread;
import com.atc.simulator.DebugDataFeed.Scenarios.Scenario;
import com.atc.simulator.Display.Model.Display;
import com.atc.simulator.Display.Model.DisplayAircraft;
import com.atc.simulator.Display.Model.DisplayHud;
import com.atc.simulator.Display.Model.DisplayPrediction;
import com.atc.simulator.Display.View.ModelInstanceProviders.HudModel;
import com.atc.simulator.Display.View.ModelInstanceProviders.WorldMapModel;
import com.atc.simulator.Display.View.ModelInstanceProviders.TracksModel;
import com.atc.simulator.flightdata.*;
import com.atc.simulator.flightdata.SystemStateDatabase.SystemStateDatabase;
import com.atc.simulator.flightdata.SystemStateDatabase.SystemStateDatabaseListener;
import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.PerformanceCounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * The DisplayApplication for the Simulator
 *
 *  - Uses LibGDX
 *
 * @author Luke Frisken
 */
public class DisplayApplication extends ApplicationAdapter implements DataPlaybackListener, PredictionListener {
    private static final boolean enableTimer = true;
    private static final boolean enableDebugPrint = ApplicationConfig.getInstance().getBoolean("settings.debug.print-display");
    private static final boolean showTracks = ApplicationConfig.getInstance().getBoolean("settings.display.show-tracks");

	private PerspectiveCamera perspectiveCamera;
    private OrthographicCamera orthoCamera;
    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private MyCameraController camController;
    private AssetManager assets;
    private Scenario scenario;
    private ArrayBlockingQueue<SystemState> systemStateUpdateQueue;
    private ArrayBlockingQueue<Prediction> predictionUpdateQueue;

    private Display display;
    private SystemStateDatabase stateDatabase;
    private LayerManager layerManager;
    private WorldMapModel map;
    private RenderLayer mapLayer;
    private TracksModel tracks;
    private RenderLayer tracksLayer;
    private RenderLayer hudLayer;
    private AircraftDatabase aircraftDatabase;
    private RenderLayer aircraftLayer;
    private RenderLayer predictionLayer;
    private HudModel hud;
    private Environment environment;

    private DataPlaybackThread playbackThread;
    private InputMultiplexer inputMultiplexer;

    private Vector2 textPosition;

    private SystemState currentSystemState = null;

    private PerformanceCounter pollSystemUpdatePerformance = new PerformanceCounter("System Update");
    private PerformanceCounter pollPredictionUpdatePerformance = new PerformanceCounter("Prediction Update");
    private PerformanceCounter renderInstancesPerformance = new PerformanceCounter("Render Instances");
    private PerformanceCounter displayUpdatePerformance = new PerformanceCounter("Display Update");
    private long frameCounter = 0;


    /**
     * private class for storing AircraftModel associated with the SystemStateDatabase,
     * and to keep them in sync with the systemstatedatabase.
     *
     *
     * May have been better to subclass SystemStateDatabase...
     *
     * @author Luke Frisken
     * @modified Chris Coleman, 14/9/16 - Added pause functionality
     * Created on 6/09/16
     */
    private class AircraftDatabase extends HashMap<String, DisplayAircraft> implements SystemStateDatabaseListener
    {
        /** @see SystemStateDatabaseListener#onSystemStateUpdate(SystemStateDatabase, ArrayList<String>) */
        @Override
        public void onSystemStateUpdate(SystemStateDatabase stateDatabase, ArrayList<String> aircraftIDs) {

        }

        /** @see SystemStateDatabaseListener#onNewAircraft(SystemStateDatabase, String) */
        @Override
        public void onNewAircraft(SystemStateDatabase stateDatabase, String aircraftID) {
            DisplayAircraft newAircraft = new DisplayAircraft(display, stateDatabase.getTrack(aircraftID));
            aircraftLayer.addDisplayRenderableProvider(newAircraft.getRenderable());
            this.put(aircraftID, newAircraft);
        }

        /** @see SystemStateDatabaseListener#onRemoveAircraft(SystemStateDatabase, String) */
        @Override
        public void onRemoveAircraft(SystemStateDatabase stateDatabase, String aircraftID) {
            DisplayAircraft displayAircraft = this.get(aircraftID);
            displayAircraft.dispose();
            aircraftLayer.removeDisplayRenderableProvider(displayAircraft.getRenderable());
            this.remove(aircraftID);
        }

        /** @see SystemStateDatabaseListener#onUpdateAircraft(SystemStateDatabase, String) */
        @Override
        public void onUpdateAircraft(SystemStateDatabase stateDatabase, String aircraftID) {
            this.get(aircraftID).update();
        }
    }

    /**
     * Constructor for the DisplayApplication
     *
     * @param scenario the scenario going to be displayed.
     * @param playbackThread The thread we need to pause if requested
     */
    public DisplayApplication(Scenario scenario, DataPlaybackThread playbackThread)
    {
        this.scenario = scenario;
        systemStateUpdateQueue = new ArrayBlockingQueue<SystemState>(100);
        predictionUpdateQueue = new ArrayBlockingQueue<Prediction>(300);
        layerManager = new LayerManager();
        stateDatabase = new SystemStateDatabase(playbackThread);
        aircraftDatabase = new AircraftDatabase();
        stateDatabase.addListener(aircraftDatabase);
        this.playbackThread = playbackThread;
        inputMultiplexer = new InputMultiplexer();
        display = new Display(playbackThread);
        display.setLayerManager(layerManager);

    }



    /**
     * This method gets called when there is a system update, and gets
     * passed the new system state
     * @param systemState the updated system state
     */
    @Override
    public void onSystemUpdate(SystemState systemState) {
        systemStateUpdateQueue.add(systemState);
    }

    /**
     * When a client receives new information, it will call this method to notify listeners
     * of a new data
     * @param newPrediction the new prediction information
     */
    @Override
    public void onPredictionUpdate(Prediction newPrediction) {
        predictionUpdateQueue.add(newPrediction);
    }


    /**
     * Private camera controller class to allow us to zoom and pan the map.
     */
    private class MyCameraController extends CameraInputController {
        private static final float MIN_FOV = 0.01f;
        private static final float MAX_FOV = 179f;


		public MyCameraController(Camera camera) {
			super(camera);
            this.pinchZoomFactor = 20f;
			zoom(0f);
		}

		@Override
		public boolean zoom(float amount)
		{
		    //some magic numbers for zooming and panning.
			float newFieldOfView = perspectiveCamera.fieldOfView - (amount * (perspectiveCamera.fieldOfView/15f));
			if (newFieldOfView < MIN_FOV || newFieldOfView > MAX_FOV)
			{
				return false;
			}

			perspectiveCamera.fieldOfView = newFieldOfView;
			perspectiveCamera.update();

			this.rotateAngle = perspectiveCamera.fieldOfView;
            display.triggerCameraOnUpdate(new DisplayCameraListener.CameraUpdate(perspectiveCamera, DisplayCameraListener.UpdateType.ZOOM));
            return true;
		}

		@Override
		protected boolean process (float deltaX, float deltaY, int button) {
			return super.process(-deltaX*1.8f, -deltaY, button);
		}
	}

	/**
     * Method create,
     * put any initialization stuff in here that relies on the opengl context
     * being available.
     */
    @Override
	public void create () {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1.0f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

	    textPosition = new Vector2(Gdx.graphics.getWidth()/2, Gdx.graphics.getHeight()/2);
		assets = new AssetManager();
//		assets.load("flight_data/CallibrateMap/CallibrateMap.csv", Track.class);
//        assets.load("assets/models/planet.g3db", LayerManager.class);
		assets.finishLoading();

        spriteBatch = new SpriteBatch();

        //load the bitmap font
//        font = new BitmapFont(new FileHandle("assets/fonts/DejaVu_Sans_Mono_12.fnt"));

        modelBatch = new ModelBatch();

        orthoCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        orthoCamera.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        orthoCamera.update();
        display.addCamera("ortho", orthoCamera);

		perspectiveCamera = new PerspectiveCamera(40, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        display.addCamera("perspective", perspectiveCamera);
		perspectiveCamera.position.set(0f, 0f, 0f);
//		Vector3 firstPos = track.get(0).getPosition().getCartesianDrawVector();
//		perspectiveCamera.lookAt(firstPos.x, firstPos.y, firstPos.z);


        //make the camera look at the projection reference (as it is likely the center of whatever's going on
        // in the scenario
        Vector3 lookAt = scenario.getProjectionReference().getCartesianDrawVector();
		perspectiveCamera.lookAt(lookAt);
		perspectiveCamera.near = 0.01f;
		perspectiveCamera.far = 2f;
		perspectiveCamera.update();

//		ModelBuilder modelBuilder = new ModelBuilder();
//		earthTextureModel = modelBuilder.createSphere(5f, 5f, 5f, 64, 64,
//				new Material(ColorAttribute.createDiffuse(Color.BLUE)),
//				VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
//


//        System.out.println("Finished loading");
//        earthTextureModel = assets.get("assets/models/planet.g3db", LayerManager.class);


//		earthTextureInstance = new ModelInstance(earthTextureModel);
//        earthTextureInstance.transform.setToScaling(-1f,1f,1f);



        camController = new MyCameraController(perspectiveCamera);
        Gdx.input.setInputProcessor(camController);


        //Set up all the RenderLayers
        mapLayer = new RenderLayer(10, "map");
        layerManager.addRenderLayer(mapLayer);
        map = new WorldMapModel(perspectiveCamera);
        mapLayer.addDisplayRenderableProvider(map);

        if (showTracks)
        {
            tracksLayer = new RenderLayer(9, "tracks");
            layerManager.addRenderLayer(tracksLayer);
            tracks = new TracksModel(perspectiveCamera, scenario);
            tracksLayer.addDisplayRenderableProvider(tracks);
        }

        aircraftLayer = new RenderLayer(7, "aircraft");
        layerManager.addRenderLayer(aircraftLayer);

        hudLayer = new RenderLayer(6, "hud");
        layerManager.addRenderLayer(hudLayer);
        //a circular dependency requires this to happen :(
        DisplayHud displayHud = new DisplayHud(display, null);
        hud = new HudModel(orthoCamera, displayHud);
        displayHud.setHudModel(hud);
        hudLayer.addDisplayRenderableProvider(hud);
        display.addCameraListener(orthoCamera, hud);
        display.setDisplayHud(displayHud);
        inputMultiplexer.addProcessor(camController);
        inputMultiplexer.addProcessor(new SimulationController());

        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    /**
     * Process any new predictions which have been placed in the prediction update queue
     * by the {@link PredictionFeedClientThread}
     */
    private void pollPredictionUpdateQueue()
    {
        if (currentSystemState == null)
        {
            return;
        }

        Prediction prediction = predictionUpdateQueue.poll();

        //poll the predictionUpdateQueue until it is empty (returning null)
        while (prediction != null)
        {
            String aircraftID = prediction.getAircraftID();

            DisplayAircraft aircraft = aircraftDatabase.get(aircraftID);

            if (aircraft != null)
            {
                DisplayPrediction displayPrediction = aircraft.getPrediction();
                if (displayPrediction != null)
                {
                    displayPrediction.update(prediction);
                } else {
                    displayPrediction = new DisplayPrediction(display, aircraft, prediction);
                    aircraft.setPrediction(displayPrediction);
                }
            }


            prediction = predictionUpdateQueue.poll();
        }
    }

    //todo: to improve draw performance this should go into the callers thread, and out of the display thread. Only push models into the queue instead.

    /**
     * process any items which have been placed in the system update queue by
     * the {@link com.atc.simulator.DebugDataFeed.DataPlaybackThread}
     */
    private void pollSystemUpdateQueue()
    {
        SystemState systemState = systemStateUpdateQueue.poll();
        currentSystemState = systemState;

        while (systemState != null)
        {
            pollSystemUpdatePerformance.start();
            stateDatabase.systemStateUpdate(systemState);
            pollSystemUpdatePerformance.stop();
            systemState = systemStateUpdateQueue.poll();

        }
    }

    /**
     * The buildMesh method for the display.
     */
	@Override
	public void render () {
        frameCounter++;
        displayUpdatePerformance.start();
        display.update();
        displayUpdatePerformance.stop();

		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        camController.update();

        pollSystemUpdateQueue();
        pollPredictionUpdatePerformance.start();
        pollPredictionUpdateQueue();
        pollPredictionUpdatePerformance.stop();


        renderInstancesPerformance.start();
        modelBatch.begin(perspectiveCamera);

        //Render all the gdxRenderableProviders in the layerManager.
        //they are in a collection of cameraBatches so that they get rendered
        //with the correct camera.
        Collection<CameraBatch> cameraBatches = layerManager.getRenderInstances();

        int nInstances = 0;

        for(CameraBatch cameraBatch: cameraBatches)
        {
            modelBatch.flush(); //buildMesh everything before switching to the new camera.

            //set the camera associated with this camera batch
            modelBatch.setCamera(cameraBatch.getCamera());
            for(RenderableProvider gdxRenderableProvider : cameraBatch.gdxRenderableProviders())
            {
                modelBatch.render(gdxRenderableProvider);
                nInstances++;
            }
        }

        display.getDisplayHud().setNumInstances(nInstances);
        modelBatch.flush();
		modelBatch.end();
        renderInstancesPerformance.stop();

//        spriteBatch.begin();
////        font.draw(spriteBatch, "Hello World", textPosition.x, textPosition.y);
//        spriteBatch.end();


//        if (frameCounter%60 == 0)
//        {
//            pollSystemUpdatePerformance.tick();
//            renderInstancesPerformance.tick();
//            displayUpdatePerformance.tick();
//            pollPredictionUpdatePerformance.tick();
//            System.out.println(
//                    Gdx.graphics.getFramesPerSecond() + ", "
//                    + pollSystemUpdatePerformance.time.latest + ", "
//                    + pollPredictionUpdatePerformance.time.latest + ", "
//                    + renderInstancesPerformance.time.latest + ", "
//                    + displayUpdatePerformance.time.latest);
//        }
	}


    /**
     * Dispose of objects which have been allocated outside of java
     */
	@Override
	public void dispose () {
		modelBatch.dispose();

        //this is throwing a concurrent modification exception, I have no idea why though...
//        layerManager.dispose();
	}

    /**
     * Allow resizing of the display window.
     * Compensate the camera for the changed window dimensions.
     * @param width
     * @param height
     */
    @Override
    public void resize(int width, int height)
    {
        float aspectRatio = (float) width / (float) height;
        perspectiveCamera.viewportWidth = 2f * aspectRatio;
        perspectiveCamera.viewportHeight = 2f;
        perspectiveCamera.update();

        orthoCamera.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        orthoCamera.update();
        display.triggerCameraOnUpdate(new DisplayCameraListener.CameraUpdate(perspectiveCamera, DisplayCameraListener.UpdateType.RESIZE));
        display.triggerCameraOnUpdate(new DisplayCameraListener.CameraUpdate(orthoCamera, DisplayCameraListener.UpdateType.RESIZE));
    }

    /**
     * Private class that handles the Input from User
     */
    private class SimulationController extends InputAdapter
    {
        /**
         * Implementation of the keyDown listener
         * @param keycode key that was pressed
         * @return whether the input was handled
         */
        @Override
        public boolean keyDown(int keycode)
        {
            switch (keycode)
            {
                case Input.Keys.SPACE:
                    playbackThread.setPaused(!playbackThread.getPaused());
                    break;
                case Input.Keys.T:
                    if(showTracks)
                        tracks.toggleTrackVisibility();
                    break;
            }

            return true;
        }
    }
}
