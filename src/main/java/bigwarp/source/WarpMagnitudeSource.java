package bigwarp.source;

import java.util.Arrays;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bigwarp.BigWarp.BigWarpData;
import bigwarp.landmarks.LandmarkTableModel;
import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import mpicbg.models.AbstractModel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

public class WarpMagnitudeSource< T extends RealType< T >> implements Source< T >
{
	protected final String name;
	
	protected final BigWarpData<?> sourceData;
	
	protected final Interval interval;
	
	protected final WarpMagnitudeRandomAccessibleInterval<T> warpMagImg;
	
	protected final VoxelDimensions voxDims;

	protected T type;
	
	public WarpMagnitudeSource( String name, BigWarpData<?> data, T t  )
	{
		this.name = name;
		this.type = t;

		sourceData = data;

		//RandomAccessibleInterval<?> fixedsrc = sourceData.sources.get( 1 ).getSpimSource().getSource( 0, 0 );
		interval = sourceData.sources.get( sourceData.targetSourceIndices[ 0 ] ).getSpimSource().getSource( 0, 0 );

		// use the interval of the fixed image
//		if( fixedsrc.dimension( 2 ) == 1 )
//			interval = new FinalInterval( 
//					new long[]{ fixedsrc.min( 0 ), fixedsrc.min( 1 ) }, 
//					new long[]{ fixedsrc.max( 0 ), fixedsrc.max( 1 ) });
//		else

		// FIXME unit fetching has been disabled
		//String unit = sourceData.sources.get( sourceData.targetSourceIndices[ 0 ] ).getSpimSource().getVoxelDimensions().unit();
		String unit = "px";
		voxDims = new FinalVoxelDimensions( unit, 1.0, 1.0, 1.0 );

		warpMagImg = new WarpMagnitudeRandomAccessibleInterval<T>( interval, t, null, null );
	}
	
	public double getMax( LandmarkTableModel lm )
	{
		double maxVal = 0.0;
		
		int ndims = lm.getNumdims();
		double[] pt = new double[ ndims ];
		
		for( Double[] movingPt : lm.getPoints( true ) )
		{
			for( int d = 0; d < ndims; d++ )
				pt[ d ] = movingPt[ d ];
			
			warpMagImg.ra.setPosition(  pt );
			double val = warpMagImg.ra.get().getRealDouble();
			
			if( val > maxVal )
				maxVal = val;
		}
		return maxVal;
	}
	
	public void setWarp( RealTransform warp )
	{
		warpMagImg.ra.warp = warp;
	}
	
	public void setBaseline( RealTransform baseline )
	{
		warpMagImg.ra.baseline = baseline;
	}
	
	public RealTransform getBaseline()
	{
		return warpMagImg.ra.baseline;
	}
	
	public void debug( double[] pt )
	{
		RealRandomAccess<T> rra = warpMagImg.realRandomAccess();
		
		rra.setPosition( pt );
		
		System.out.println("at : " + Arrays.toString( pt ) );
		System.out.println( "get val: " + rra.get());
		double[] baseRes = new double[ warpMagImg.ra.warp.numTargetDimensions() ]; 
		warpMagImg.ra.baseline.apply( pt, baseRes );
		
		double[] warpRes = new double[ warpMagImg.ra.warp.numTargetDimensions() ]; 
		warpMagImg.ra.warp.apply( pt, warpRes );

		System.out.println( "base res: " + baseRes[0] + " " + baseRes[1]);
		System.out.println( "warp res: " + warpRes[0] + " " + warpRes[1]);
		
	}

	public double[] minMax()
	{
		double[] minmax = new double[ 2 ];
		minmax[ 0 ] = Double.MAX_VALUE;
		minmax[ 1 ] = Double.MIN_VALUE;
		
		Cursor<T> curs = Views.iterable( this.getSource( 0,0 ) ).cursor();
		
		while( curs.hasNext() )
		{
			double val = curs.next().getRealDouble();
			if( val < minmax[ 0 ])
				minmax[ 0 ] = val;
			else if( val > minmax[ 1 ])
				minmax[ 1 ] = val;
			
		}
		return minmax;
	}
	
	@Override
	public boolean isPresent( int t )
	{
		return ( t == 0 );
	}
	
	/*
	public Interval estimateBoundingInterval( final int t, final int level )
	{
		final Interval interval = getSource( t, level );
		
		System.out.println( "source Interval:  [ " + interval.min( 0 ) + ", " + interval.max( 0 ) +
				" ]  x  [ " + interval.min( 1 ) + ", " + interval.max( 1 ) +" ] ");
		
		long[] delta = new long[ interval.numDimensions() ];
		Arrays.fill( delta, 1 );
		
		return ((ThinPlateR2LogRSplineKernelTransform)warpMagImg.ra.warp).estimateBoundingBox( interval, delta );
	}
	 */
	
	@Override
	public RandomAccessibleInterval<T> getSource( int t, int level ) 
	{
		return Views.interval( Views.raster( 
				getInterpolatedSource( t, level, Interpolation.NEARESTNEIGHBOR ) ), 
				interval );
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource( int t, int level, Interpolation method ) 
	{
		return warpMagImg;
	}

	@Override
	public void getSourceTransform( int t, int level, AffineTransform3D transform )
	{
		transform.identity();
	}

	@Override
	public T getType()
	{
		return type;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return voxDims;
	}

	@Override
	public int getNumMipmapLevels() 
	{
		return 1;
	}
	
}
