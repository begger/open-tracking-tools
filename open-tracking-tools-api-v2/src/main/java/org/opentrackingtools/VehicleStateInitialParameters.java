package org.opentrackingtools;

import gov.sandia.cognition.math.matrix.Vector;

public class VehicleStateInitialParameters {

  private final Vector obsCov;
  private final Vector onRoadStateCov;
  private final Vector offRoadStateCov;
  private final Vector offTransitionProbs;
  private final Vector onTransitionProbs;
  private final long seed;
  private final int numParticles;
  private final String particleFilterTypeName;
  private final String roadFilterTypeName;
  private final int initialObsFreq;
  private final int obsCovDof;
  private final int onRoadCovDof;
  private final int offRoadCovDof;

  public VehicleStateInitialParameters(Vector obsCov, int obsCovDof,
    Vector onRoadStateCov, int onRoadCovDof, Vector offRoadStateCov,
    int offRoadCovDof, Vector offProbs, Vector onProbs,
    String particleFilterTypeName, String roadFilterTypeName,
    int numParticles, int initialObsFreq, long seed) {
    this.obsCovDof = obsCovDof;
    this.onRoadCovDof = onRoadCovDof;
    this.offRoadCovDof = offRoadCovDof;
    this.numParticles = numParticles;
    this.obsCov = obsCov;
    this.onRoadStateCov = onRoadStateCov;
    this.offRoadStateCov = offRoadStateCov;
    this.offTransitionProbs = offProbs;
    this.onTransitionProbs = onProbs;
    this.seed = seed;
    this.particleFilterTypeName = particleFilterTypeName;
    this.roadFilterTypeName = roadFilterTypeName;
    this.initialObsFreq = initialObsFreq;
  }

  public String getParticleFilterTypeName() {
    return particleFilterTypeName;
  }

  public String getRoadFilterTypeName() {
    return roadFilterTypeName;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final VehicleStateInitialParameters other =
        (VehicleStateInitialParameters) obj;
    if (particleFilterTypeName == null) {
      if (other.particleFilterTypeName != null) {
        return false;
      }
    } else if (!particleFilterTypeName
        .equals(other.particleFilterTypeName)) {
      return false;
    }
    if (roadFilterTypeName == null) {
      if (other.roadFilterTypeName != null) {
        return false;
      }
    } else if (!roadFilterTypeName.equals(other.roadFilterTypeName)) {
      return false;
    }
    if (initialObsFreq != other.initialObsFreq) {
      return false;
    }
    if (numParticles != other.numParticles) {
      return false;
    }
    if (obsCov == null) {
      if (other.obsCov != null) {
        return false;
      }
    } else if (!obsCov.equals(other.obsCov)) {
      return false;
    }
    if (obsCovDof != other.obsCovDof) {
      return false;
    }
    if (offRoadCovDof != other.offRoadCovDof) {
      return false;
    }
    if (offRoadStateCov == null) {
      if (other.offRoadStateCov != null) {
        return false;
      }
    } else if (!offRoadStateCov.equals(other.offRoadStateCov)) {
      return false;
    }
    if (offTransitionProbs == null) {
      if (other.offTransitionProbs != null) {
        return false;
      }
    } else if (!offTransitionProbs.equals(other.offTransitionProbs)) {
      return false;
    }
    if (onRoadCovDof != other.onRoadCovDof) {
      return false;
    }
    if (onRoadStateCov == null) {
      if (other.onRoadStateCov != null) {
        return false;
      }
    } else if (!onRoadStateCov.equals(other.onRoadStateCov)) {
      return false;
    }
    if (onTransitionProbs == null) {
      if (other.onTransitionProbs != null) {
        return false;
      }
    } else if (!onTransitionProbs.equals(other.onTransitionProbs)) {
      return false;
    }
    if (seed != other.seed) {
      return false;
    }
    return true;
  }

  public int getInitialObsFreq() {
    return this.initialObsFreq;
  }

  public int getNumParticles() {
    return numParticles;
  }

  public Vector getObsCov() {
    return obsCov;
  }

  public int getObsCovDof() {
    return this.obsCovDof;
  }

  public int getOffRoadCovDof() {
    return offRoadCovDof;
  }

  public Vector getOffRoadStateCov() {
    return offRoadStateCov;
  }

  public Vector getOffTransitionProbs() {
    return offTransitionProbs;
  }

  public int getOnRoadCovDof() {
    return onRoadCovDof;
  }

  public Vector getOnRoadStateCov() {
    return onRoadStateCov;
  }

  public Vector getOnTransitionProbs() {
    return onTransitionProbs;
  }

  public long getSeed() {
    return seed;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result =
        prime
            * result
            + ((particleFilterTypeName == null) ? 0
                : particleFilterTypeName.hashCode());
    result =
        prime
            * result
            + ((roadFilterTypeName == null) ? 0 : roadFilterTypeName
                .hashCode());
    result = prime * result + initialObsFreq;
    result = prime * result + numParticles;
    result =
        prime * result + ((obsCov == null) ? 0 : obsCov.hashCode());
    result = prime * result + obsCovDof;
    result = prime * result + offRoadCovDof;
    result =
        prime
            * result
            + ((offRoadStateCov == null) ? 0 : offRoadStateCov
                .hashCode());
    result =
        prime
            * result
            + ((offTransitionProbs == null) ? 0 : offTransitionProbs
                .hashCode());
    result = prime * result + onRoadCovDof;
    result =
        prime
            * result
            + ((onRoadStateCov == null) ? 0 : onRoadStateCov
                .hashCode());
    result =
        prime
            * result
            + ((onTransitionProbs == null) ? 0 : onTransitionProbs
                .hashCode());
    result = prime * result + (int) (seed ^ (seed >>> 32));
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("VehicleStateInitialParameters [obsCov=")
        .append(obsCov).append(", onRoadStateCov=")
        .append(onRoadStateCov).append(", offRoadStateCov=")
        .append(offRoadStateCov).append(", offTransitionProbs=")
        .append(offTransitionProbs).append(", onTransitionProbs=")
        .append(onTransitionProbs).append(", seed=").append(seed)
        .append(", numParticles=").append(numParticles)
        .append(", particleFilterTypeName=")
        .append(particleFilterTypeName)
        .append(", roadFilterTypeName=").append(roadFilterTypeName)
        .append(", initialObsFreq=").append(initialObsFreq)
        .append(", obsCovDof=").append(obsCovDof)
        .append(", onRoadCovDof=").append(onRoadCovDof)
        .append(", offRoadCovDof=").append(offRoadCovDof).append("]");
    return builder.toString();
  }
}