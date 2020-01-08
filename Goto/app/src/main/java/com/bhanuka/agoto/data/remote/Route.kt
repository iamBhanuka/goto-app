package com.bhanuka.agoto.data.remote

data class Route(
    val bounds: Bounds,
    val legs: List<Leg>,
    val overview_polyline: OverviewPolyline
)