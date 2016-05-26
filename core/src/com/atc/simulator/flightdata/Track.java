package com.atc.simulator.flightdata;

import com.atc.simulator.vectors.GeographicCoordinate;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * Created by luke on 7/04/16.
 * Represents a continuous track of an aircraft as it flies through the air, with regular
 * TrackEntry's representing the state of the aircraft for each point in time.
 *
 * @author Luke Frisken
 */
public class Track extends ArrayList<AircraftState> {

    /**
     * Generate a GL_LINES model of the track
     * @return
     */
    public Model getModel(){
        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();
        MeshPartBuilder builder = modelBuilder.part(
                "track",
                GL20.GL_LINES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked,
                new Material());
        builder.setColor(Color.RED);

        //jump, just in case we want to skip some elements (it was having trouble drawing the entire track)
        //for performance reasons.
        int jump = 1;
        Vector3 previousPositionDrawVector = this.get(0).getPosition().getModelDrawVector();
        for(int i = jump; i < this.size(); i+=jump)
        {
            AircraftState state = this.get(i);
//            System.out.println(state.getPosition());
            Vector3 positionDrawVector = state.getPosition().getModelDrawVector();
//            System.out.println(previousPositionDrawVector.len());
//            System.out.println(positionDrawVector.len());
//            System.out.println(positionDrawVector);
            builder.line(previousPositionDrawVector, positionDrawVector);
            previousPositionDrawVector = positionDrawVector;
        }

        return modelBuilder.end();
    }

}
