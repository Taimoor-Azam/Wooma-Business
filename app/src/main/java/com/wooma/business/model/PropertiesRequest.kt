package com.wooma.business.model

import android.os.Parcel
import android.os.Parcelable

data class PropertiesRequest(
    var address: String,
    var address_line_2: String,
    var city: String,
    var postcode: String,
    var created_by: String?,
)

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
) : Parcelable {

    constructor(parcel: Parcel) : this(
        postcode = parcel.readString() ?: "",
        postcode_inward = parcel.readString() ?: "",
        postcode_outward = parcel.readString() ?: "",
        post_town = parcel.readString() ?: "",
        dependant_locality = parcel.readString() ?: "",
        double_dependant_locality = parcel.readString() ?: "",
        thoroughfare = parcel.readString() ?: "",
        dependant_thoroughfare = parcel.readString() ?: "",
        building_number = parcel.readString() ?: "",
        building_name = parcel.readString() ?: "",
        sub_building_name = parcel.readString() ?: "",
        po_box = parcel.readString() ?: "",
        department_name = parcel.readString() ?: "",
        organisation_name = parcel.readString() ?: "",
        udprn = parcel.readInt(),
        postcode_type = parcel.readString() ?: "",
        su_organisation_indicator = parcel.readString() ?: "",
        delivery_point_suffix = parcel.readString() ?: "",
        line_1 = parcel.readString() ?: "",
        line_2 = parcel.readString() ?: "",
        line_3 = parcel.readString() ?: "",
        premise = parcel.readString() ?: "",
        longitude = parcel.readDouble(),
        latitude = parcel.readDouble(),
        eastings = parcel.readInt(),
        northings = parcel.readInt(),
        country = parcel.readString() ?: "",
        traditional_county = parcel.readString() ?: "",
        administrative_county = parcel.readString() ?: "",
        postal_county = parcel.readString() ?: "",
        county = parcel.readString() ?: "",
        district = parcel.readString() ?: "",
        ward = parcel.readString() ?: "",
        uprn = parcel.readString() ?: "",
        id = parcel.readString() ?: "",
        country_iso = parcel.readString() ?: "",
        country_iso_2 = parcel.readString() ?: "",
        county_code = parcel.readString() ?: "",
        language = parcel.readString() ?: "",
        umprn = parcel.readString() ?: "",
        dataset = parcel.readString() ?: ""
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(postcode)
        parcel.writeString(postcode_inward)
        parcel.writeString(postcode_outward)
        parcel.writeString(post_town)
        parcel.writeString(dependant_locality)
        parcel.writeString(double_dependant_locality)
        parcel.writeString(thoroughfare)
        parcel.writeString(dependant_thoroughfare)
        parcel.writeString(building_number)
        parcel.writeString(building_name)
        parcel.writeString(sub_building_name)
        parcel.writeString(po_box)
        parcel.writeString(department_name)
        parcel.writeString(organisation_name)
        parcel.writeInt(udprn)
        parcel.writeString(postcode_type)
        parcel.writeString(su_organisation_indicator)
        parcel.writeString(delivery_point_suffix)
        parcel.writeString(line_1)
        parcel.writeString(line_2)
        parcel.writeString(line_3)
        parcel.writeString(premise)
        parcel.writeDouble(longitude)
        parcel.writeDouble(latitude)
        parcel.writeInt(eastings)
        parcel.writeInt(northings)
        parcel.writeString(country)
        parcel.writeString(traditional_county)
        parcel.writeString(administrative_county)
        parcel.writeString(postal_county)
        parcel.writeString(county)
        parcel.writeString(district)
        parcel.writeString(ward)
        parcel.writeString(uprn)
        parcel.writeString(id)
        parcel.writeString(country_iso)
        parcel.writeString(country_iso_2)
        parcel.writeString(county_code)
        parcel.writeString(language)
        parcel.writeString(umprn)
        parcel.writeString(dataset)
    }

    companion object CREATOR : Parcelable.Creator<PostalAddress> {
        override fun createFromParcel(parcel: Parcel): PostalAddress = PostalAddress(parcel)
        override fun newArray(size: Int): Array<PostalAddress?> = arrayOfNulls(size)
    }
}
