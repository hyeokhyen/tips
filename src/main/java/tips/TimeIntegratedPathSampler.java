package tips;

import java.util.List;
import java.util.Random;


import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jblas.DoubleMatrix;
import org.jblas.MatrixFunctions;

import tips.utils.ProcessUtils;


import briefj.collections.Counter;
 



public class TimeIntegratedPathSampler<S>
{
  public int nParticles = 1000;
  public Proposal<S> proposal;
  public Process<S> process;
  public Random rand = new Random(1);
  
  public TimeIntegratedPathSampler(Proposal<S> proposal, Process<S> process)
  {
    this.proposal = proposal;
    this.process = process;
  }
  
  public Counter<List<S>> sample(S x, S y, double t)
  {
    return sample(x,y,t,null);
  }

  public Counter<List<S>> sample(S x, S y, double t, SummaryStatistics weightVariance)
  {
    return importanceSample(rand, nParticles, process, proposal, x, y, t,  weightVariance);
  }
  
  public double estimateZ(S x, S y, double t, SummaryStatistics weightVariance)
  {
    double sum = (Double) importanceSample(rand, nParticles, process, proposal, x, y, t, weightVariance, false);
    return sum/((double) nParticles);
  }
  
  public double estimateZ(Counter<List<S>> samples)
  {
    return samples.totalCount() / nParticles;
  }
  
  public double estimateZ(S x, S y, double t)
  {
    return estimateZ(x, y, t, null);
  }
  
  public static <S> Counter<List<S>> importanceSample(
      Random rand, int nParticles, Process<S> process, 
      Proposal<S> proposal, S x, S y, double t)
  {
    return importanceSample(
        rand,  nParticles,  process, 
         proposal,  x,  y,  t, null);
  }
  
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public static <S> Counter<List<S>> importanceSample(
      Random rand, int nParticles, Process<S> process, 
      Proposal<S> proposal, S x, S y, double t, SummaryStatistics weightVariance)
  {
    return (Counter) importanceSample(rand, nParticles, process, proposal, x, y, t, weightVariance, true);
  }
  
  private static <S> Object importanceSample(
      Random rand, int nParticles, Process<S> process, 
      Proposal<S> proposal, S x, S y, double t, SummaryStatistics weightVariance, boolean keepSample)
  {
    Counter<List<S>> result = keepSample ? new Counter<List<S>>() : null;
    double sum = 0.0;
    
    for (int pIdx = 0; pIdx < nParticles; pIdx++)
    {
      Pair<List<S>, Double> proposed = proposal.propose(rand, x, y, t);
      double integral = integral(process, proposed.getLeft(), t);
      List<S> proposedJumps = proposed.getLeft();
      for (int jIdx = 0; jIdx < proposedJumps.size() - 1; jIdx++)
        integral *= ProcessUtils.transitionProbability(process, proposedJumps.get(jIdx), proposedJumps.get(jIdx+1));
      
      if (weightVariance != null) weightVariance.addValue(integral/proposed.getRight());
      
      if (keepSample)
        result.incrementCount(proposed.getLeft(), integral/proposed.getRight());
      else
        sum += integral/proposed.getRight();
    }
    
    return keepSample ? result : sum;
  }
  
  public static <S> double integral(Process<S> process, List<S> proposed, double t)
  {
    // build matrix
    final int size = proposed.size() + 1;
    double [][] mtx = new double[size][size];
    for (int i = 0; i < proposed.size(); i++)
    {
      final double curRate = ProcessUtils.holdRate(process, proposed.get(i));
      mtx[i][i] = -curRate * t;
      mtx[i][i+1] = curRate * t;
    }
    
    return MatrixFunctions.expm(new DoubleMatrix(mtx)).get(0, size - 2);
  }

}