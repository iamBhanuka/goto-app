package com.bhanuka.agoto.data.remote

data class Leg(
    val distance: Distance,
    val duration: Duration,
    val end_address: String,
    val end_location: EndLocation,
    val start_address: String,
    val start_location: StartLocation
)