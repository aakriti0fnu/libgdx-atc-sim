package com.atc.simulator.vectors;

import pythagoras.d.Matrix3;
import pythagoras.d.Vector;
import pythagoras.d.Vector3;

/**
 * Created by luke on 7/05/16.
 *
 * @author Luke Frisken
 */
public class SphericalVelocity extends Vector3 {

    public SphericalVelocity(SphericalVelocity other)
    {
        super(other);
    }

    public SphericalVelocity(Vector3 other)
    {
        super(other);
    }

    public SphericalVelocity(double dr, double dtheta, double dphi) {
        this.x = dr;
        this.y = dtheta;
        this.z = dphi;
    }

    /**
     * Get Delta Radius Component
     * @return
     */
    public double getDR() {
        return this.x;
    }

    /**
     * Get Delta Theta Component
     * @return
     */
    public double getDTheta() {
        return this.y;
    }

    /**
     * Get Delta Phi Component
     * @return
     */
    public double getDPhi() {
        return this.z;
    }


    public double getSpeed()
    {
        return this.getCartesian(new SphericalCoordinate(new Vector3(0,0,0))).length();
    }

    /**
     * Get the cartesian libgdx vector3 required for 3D drawing.
     * @return
     */
    public com.badlogic.gdx.math.Vector3 getCartesianDrawVector(SphericalCoordinate from)
    {
        System.out.println("Coords after transform DR:"+this.getDR()+" DTheta:"+this.getDTheta()+" DPhi:" + this.getDPhi());
        Vector3 cartesian = this.getCartesian(from);
        return new com.badlogic.gdx.math.Vector3((float) cartesian.x, (float) cartesian.z, (float) cartesian.y);
    }

    /**
     * Get the cartesian velocity
     * @param from the point of reference for which this velocity is based
     * @return
     */
    public Vector3 getCartesian(SphericalCoordinate from)
    {
        //v = dr * e_r + r*dtheta*sin(phi)*e_theta + r*dphi*e_phi
        return from.rCartesianUnitVector().mult(x).add(
                from.thetaCartesianUnitVector().mult(from.x * y * Math.sin(from.z)).add(
                        from.phiCartesianUnitVector().mult(from.x * z)
                )
        );
    }


    /**
     * Get the equivalent cartesian angular velocity.
     * Using math from here:
     * http://dynref.engr.illinois.edu/rvs.html
     * @param from
     * @return
     */
    public Vector3 getCartesianAngularVelocity(SphericalCoordinate from)
    {
        //w = dphi*e_theta + dtheta*k
        return from.thetaCartesianUnitVector().mult(getDPhi()).add(new Vector3(0, 0, 1).mult(getDTheta()));
    }


    /**
     * Rotate a spherical coordinate using this Spherical velocity as an angular velocity
     * around a sphere.
     * @param from the point to translate
     * @param t the amount of time in seconds (resulting in the angle to rotate).
     * @return the rotated coordinate
     */
    public SphericalCoordinate angularVelocityTranslate(SphericalCoordinate from, double t)
    {
        Vector3 cartesianPosition = from.getCartesian();
        Vector3 angularVelocity = getCartesianAngularVelocity(from);
        double dangle = angularVelocity.length();
        Vector3 angleAxis = angularVelocity.normalize();
        Matrix3 rotation = new Matrix3().setToRotation(dangle*t, angleAxis);

        Vector3 cartesianTranslatedPosition = rotation.transform(cartesianPosition);
        return SphericalCoordinate.fromCartesian(cartesianTranslatedPosition);
    }

    /**
     * whether or not any of the elements in this velocity vector are NaN
     * @return
     */
    public boolean isNaN()
    {
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z);
    }
}
