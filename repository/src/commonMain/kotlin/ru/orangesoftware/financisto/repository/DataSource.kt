package ru.orangesoftware.financisto.repository

sealed interface DataSource
interface LocalDataSource : DataSource
interface RemoteDataSource : DataSource
