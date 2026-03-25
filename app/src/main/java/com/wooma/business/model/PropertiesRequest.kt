package com.wooma.business.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class PropertiesRequest(
    var address: String,
    var address_line_2: String,
    var city: String,
    var postcode: String,
    var created_by: String?,
)

@Parcelize
data class PostalAddress(
    val postcode: String,
    val postcode_inward: String,
    val postcode_outward: String,
    val post_town: String,
    val dependant_locality: String,
    val double_dependant_locality: String,
    val thoroughfare: String,
    val dependant_thoroughfare: String,
    val building_number: String,
    val building_name: String,
    val sub_building_name: String,
    val po_box: String,
    val department_name: String,
    val organisation_name: String,
    val udprn: Int,
    val postcode_type: String,
    val su_organisation_indicator: String,
    val delivery_point_suffix: String,
    val line_1: String,
    val line_2: String,
    val line_3: String,
    val premise: String,
    val longitude: Double,
    val latitude: Double,
    val eastings: Int,
    val northings: Int,
    val country: String,
    val traditional_county: String,
    val administrative_county: String,
    val postal_county: String,
    val county: String,
    val district: String,
    val ward: String,
    val uprn: String,
    val id: String,
    val country_iso: String,
    val country_iso_2: String,
    val county_code: String,
    val language: String,
    val umprn: String,
    val dataset: String
) : Parcelable