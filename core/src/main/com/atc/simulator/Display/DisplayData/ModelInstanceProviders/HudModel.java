package com.atc.simulator.Display.DisplayData.ModelInstanceProviders;

import com.atc.simulator.Display.Display;
import com.atc.simulator.Display.DisplayCameraListener;
import com.atc.simulator.Display.DisplayData.DisplayAircraft;
import com.atc.simulator.vectors.GeographicCoordinate;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

/**
 * Created by luke on 8/09/16.
 */
public class HudModel extends ModelInstanceDisplayRenderableProvider implements DisplayCameraListener {
    private Display display;
    /**
     * Cnstructor of TracksModel
     * @param camera the camera used to draw this model
     * @param display the display this model is being rendered on.
     */
    public HudModel(Camera camera, Display display)
    {
        super(camera);
        this.display = display;
        update();
    }

    /**
     * Call to update the instance provided by this class.
     */
    @Override
    public void update()
    {
        super.update();

        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();
        MeshPartBuilder builder = modelBuilder.part(
                "aircraft_info",
                GL20.GL_LINES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked,
                new Material());
        builder.setColor(Color.WHITE);

        float crossHairSize = 10;

        Vector3 centre = new Vector3(Gdx.graphics.getWidth()/2, Gdx.graphics.getHeight()/2, 0);
        Vector3 crossHairTop = new Vector3(centre).add(new Vector3(0f, crossHairSize, 0f));
        Vector3 crossHairBottom = new Vector3(centre).add(new Vector3(0f, -crossHairSize, 0f));
        Vector3 crossHairLeft = new Vector3(centre).add(new Vector3(-crossHairSize, 0f, 0f));
        Vector3 crossHairRight = new Vector3(centre).add(new Vector3(crossHairSize, 0f, 0f));

        builder.line(crossHairBottom, crossHairTop);
        builder.line(crossHairLeft, crossHairRight);
//        builder.line(new Vector3(0, 0, -1), new Vector3(0, 0, 1));

        Model newModel = modelBuilder.end();
        ModelInstance modelInstance = setModel(newModel, false);

        triggerOnInstanceUpdate(getDisplayRenderable());
    }

    @Override
    public void onUpdate(Camera camera, UpdateType updateType) {
        System.out.println("update" + updateType.name());
        update();
    }
}
